package com.ligalytics.betting;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.auth.User;
import com.ligalytics.auth.UserRepository;
import com.ligalytics.betting.BzzoiroOddsProvider.OddsEvent;
import com.ligalytics.etl.BzzoiroClient;
import com.ligalytics.etl.TeamNameNormalizer;
import com.ligalytics.model.Match;
import com.ligalytics.model.Team;
import com.ligalytics.patterns.facade.LigaLyticsFacade;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.repository.TeamRepository;
import com.ligalytics.service.dto.PredictionResponseDto;

/**
 * Apuestas de demostración con saldo ficticio. Reúne las cuotas reales (consenso de casas, Bzzoiro), las
 * probabilidades del modelo, las recomendaciones, el registro de apuestas y su liquidación cuando el
 * partido termina. La cuota siempre la fija el servidor: el cliente no puede elegirla.
 */
@Service
public class BettingService {

    /** Cuota ofrecida con la probabilidad del modelo y su ventaja ({@code p * cuota - 1}). */
    /**
     * Cuota ofrecida con las probabilidades implicadas.
     *
     * @param modelProbability    probabilidad bruta de nuestro modelo
     * @param marketProbability   probabilidad implicita de las casas sin margen (nula si no hay mercado completo)
     * @param adjustedProbability mezcla 50/50 de modelo y mercado: el modelo no esta demostrado, asi que se ancla al mercado
     * @param edge                {@code adjustedProbability * cuota - 1}
     */
    public record Offer(OddsLine line, Double modelProbability, Double marketProbability, Double adjustedProbability,
            Double edge) {
    }

    /** Partido con sus cuotas. */
    public record BoardMatch(OddsEvent event, Long homeTeamId, Long awayTeamId, String homeName, String awayName,
            boolean hasModel, List<Offer> offers) {
    }

    /** Selección recomendada, con un importe sugerido para el saldo del usuario. */
    public record Recommendation(BoardMatch match, Offer offer, long suggestedStake) {
    }

    public static final long MIN_STAKE = 1_000L;
    static final double MIN_EDGE = 0.03;
    /** Ventajas mayores suelen ser errores del modelo, no oportunidades: no se recomiendan. */
    static final double MAX_EDGE = 0.25;
    /** Si modelo y mercado difieren en mas de 15 puntos, se considera una discrepancia sospechosa. */
    static final double MAX_DISCREPANCY = 0.15;
    static final double MIN_PROBABILITY = 0.30;
    static final double KELLY_FRACTION = 0.25;
    static final double MAX_STAKE_SHARE = 0.05;
    private static final Duration SETTLE_AFTER_KICKOFF = Duration.ofHours(2);
    private static final Duration VOID_AFTER = Duration.ofDays(3);
    private static final Logger log = LoggerFactory.getLogger(BettingService.class);

    private final BzzoiroOddsProvider oddsProvider;
    private final TheOddsApiCardsProvider cardsProvider;
    private final BzzoiroClient bzzoiro;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final LigaLyticsFacade facade;
    private final UserRepository users;
    private final BetRepository bets;
    private final ParlayRepository parlays;

    /** Selección pedida para una combinada: el cliente solo indica qué quiere, la cuota la pone el servidor. */
    public record LegRequest(long eventId, Market market, String selection, Double line) {
    }

    public static final int MIN_LEGS = 2;
    public static final int MAX_LEGS = 8;
    public static final double MAX_TOTAL_ODDS = 1000.0;

    public BettingService(BzzoiroOddsProvider oddsProvider, TheOddsApiCardsProvider cardsProvider, BzzoiroClient bzzoiro,
            TeamRepository teamRepository, MatchRepository matchRepository, LigaLyticsFacade facade, UserRepository users,
            BetRepository bets, ParlayRepository parlays) {
        this.parlays = parlays;
        this.oddsProvider = oddsProvider;
        this.cardsProvider = cardsProvider;
        this.bzzoiro = bzzoiro;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.facade = facade;
        this.users = users;
        this.bets = bets;
    }

    public boolean isConfigured() {
        return oddsProvider.isConfigured();
    }

    /** Próximos partidos con sus cuotas, probabilidades del modelo y ventaja. */
    @Transactional(readOnly = true)
    public List<BoardMatch> board(int days) {
        List<BoardMatch> board = new ArrayList<>();
        for (OddsEvent event : oddsProvider.upcoming(Math.max(1, Math.min(days, 14)))) {
            BoardMatch match = buildBoard(event);
            if (!match.offers().isEmpty()) {
                board.add(match);
            }
        }
        return board;
    }

    /** Un partido con todos sus mercados; vacío si ya no está disponible para apostar. */
    @Transactional(readOnly = true)
    public Optional<BoardMatch> boardMatch(long eventId) {
        return oddsProvider.upcoming(14).stream().filter(e -> e.id() == eventId).findFirst().map(this::buildBoard);
    }

    /** Las mejores ventajas del modelo frente a las cuotas, con un importe sugerido (¼ de Kelly, máximo 5 % del saldo). */
    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(User user) {
        long balance = users.findById(user.getId()).map(User::getBalance).orElse(0L);
        List<Recommendation> result = new ArrayList<>();
        for (BoardMatch match : board(7)) {
            for (Offer offer : match.offers()) {
                if (isRecommendable(offer)) {
                    result.add(new Recommendation(match, offer, suggestedStake(offer, balance)));
                }
            }
        }
        result.sort(Comparator.comparingDouble((Recommendation r) -> r.offer().edge()).reversed());
        return result.stream().limit(15).toList();
    }

    /** Ventaja moderada, sobre una cuota real, sin gran discrepancia entre el modelo y el mercado. */
    public static boolean isRecommendable(Offer offer) {
        return offer.edge() != null && offer.marketProbability() != null && offer.line().isReal()
                && offer.edge() >= MIN_EDGE && offer.edge() <= MAX_EDGE
                && Math.abs(offer.modelProbability() - offer.marketProbability()) <= MAX_DISCREPANCY
                && offer.adjustedProbability() >= MIN_PROBABILITY;
    }

    /** Importe sugerido: ¼ de Kelly acotado al 5 % del saldo, redondeado a miles y nunca por debajo del mínimo. */
    static long suggestedStake(Offer offer, long balance) {
        double odds = offer.line().odds();
        double kelly = (offer.adjustedProbability() * odds - 1.0) / (odds - 1.0);
        double share = Math.min(MAX_STAKE_SHARE, Math.max(0.0, KELLY_FRACTION * kelly));
        long stake = (long) (Math.floor(balance * share / MIN_STAKE) * MIN_STAKE);
        return Math.min(Math.max(stake, MIN_STAKE), Math.max(balance, 0));
    }

    /** Registra una apuesta descontando el importe del saldo ficticio. */
    @Transactional
    public Bet placeBet(Long userId, long eventId, Market market, String selection, Double line, long stake) {
        if (stake < MIN_STAKE) {
            throw new IllegalArgumentException("El importe mínimo es " + MIN_STAKE + " COP");
        }
        OddsEvent event = oddsProvider.upcoming(14).stream().filter(e -> e.id() == eventId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El partido no está disponible para apostar"));
        if (!event.kickoff().isAfter(Instant.now())) {
            throw new IllegalArgumentException("El partido ya ha empezado");
        }
        BoardMatch match = buildBoard(event);
        Offer offer = match.offers().stream().filter(o -> o.line().sameAs(market, selection, line)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Esa cuota no está disponible"));

        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
        if (user.getBalance() < stake) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }
        user.setBalance(user.getBalance() - stake);
        users.save(user);

        Bet bet = new Bet();
        bet.setUserId(userId);
        bet.setEventId(eventId);
        bet.setHomeTeam(match.homeName());
        bet.setAwayTeam(match.awayName());
        bet.setKickoff(event.kickoff());
        bet.setMarket(market);
        bet.setSelection(selection.toUpperCase());
        bet.setLine(offer.line().line());
        bet.setOdds(offer.line().odds());
        bet.setOddsSource(offer.line().source());
        bet.setStake(stake);
        return bets.save(bet);
    }

    @Transactional(readOnly = true)
    public List<Bet> betsOf(Long userId) {
        return bets.findTop100ByUserIdOrderByPlacedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Parlay> parlaysOf(Long userId) {
        return parlays.findTop100ByUserIdOrderByPlacedAtDesc(userId);
    }

    /**
     * Registra una combinada: de 2 a 8 selecciones de partidos distintos (dos selecciones del mismo partido están
     * correlacionadas y no se combinan). La cuota total es el producto de las cuotas que fija el servidor.
     */
    @Transactional
    public Parlay placeParlay(Long userId, long stake, List<LegRequest> requests) {
        if (stake < MIN_STAKE) {
            throw new IllegalArgumentException("El importe mínimo es " + MIN_STAKE + " COP");
        }
        if (requests == null || requests.size() < MIN_LEGS || requests.size() > MAX_LEGS) {
            throw new IllegalArgumentException("Una combinada lleva entre " + MIN_LEGS + " y " + MAX_LEGS + " selecciones");
        }
        if (requests.stream().map(LegRequest::eventId).distinct().count() != requests.size()) {
            throw new IllegalArgumentException("No se pueden combinar dos selecciones del mismo partido");
        }
        List<OddsEvent> upcoming = oddsProvider.upcoming(14);
        Parlay parlay = new Parlay();
        double total = 1.0;
        for (LegRequest request : requests) {
            OddsEvent event = upcoming.stream().filter(e -> e.id() == request.eventId()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Un partido de la combinada no está disponible para apostar"));
            if (!event.kickoff().isAfter(Instant.now())) {
                throw new IllegalArgumentException("Un partido de la combinada ya ha empezado");
            }
            BoardMatch match = buildBoard(event);
            Offer offer = match.offers().stream()
                    .filter(o -> o.line().sameAs(request.market(), request.selection(), request.line())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Una cuota de la combinada ya no está disponible (" + match.homeName() + " - " + match.awayName() + ")"));
            total *= offer.line().odds();

            ParlayLeg leg = new ParlayLeg();
            leg.setEventId(event.id());
            leg.setHomeTeam(match.homeName());
            leg.setAwayTeam(match.awayName());
            leg.setKickoff(event.kickoff());
            leg.setMarket(request.market());
            leg.setSelection(request.selection().toUpperCase());
            leg.setLine(offer.line().line());
            leg.setOdds(offer.line().odds());
            leg.setOddsSource(offer.line().source());
            parlay.addLeg(leg);
        }
        total = Math.round(total * 100.0) / 100.0;
        if (total > MAX_TOTAL_ODDS) {
            throw new IllegalArgumentException("La cuota total máxima de una combinada es " + (long) MAX_TOTAL_ODDS);
        }

        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
        if (user.getBalance() < stake) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }
        user.setBalance(user.getBalance() - stake);
        users.save(user);

        parlay.setUserId(userId);
        parlay.setStake(stake);
        parlay.setTotalOdds(total);
        return parlays.save(parlay);
    }

    /** Devuelve el saldo a 100 000 COP ficticios y borra el historial de apuestas de la cuenta. */
    @Transactional
    public long resetWallet(Long userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
        bets.deleteByUserId(userId);
        parlays.deleteByUserId(userId);
        user.setBalance(User.INITIAL_BALANCE);
        users.save(user);
        return user.getBalance();
    }

    /** Liquidación de respaldo cada 30 minutos (la principal ocurre cuando el ETL guarda un partido terminado). */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT30M")
    public void scheduledSettlement() {
        try {
            settlePending();
        } catch (RuntimeException ex) {
            log.warn("No se pudieron liquidar las apuestas: {}", ex.getMessage());
        }
    }

    /**
     * Liquida las apuestas pendientes cuyo partido ya terminó: goles y ganador con el marcador guardado;
     * córneres y tarjetas con los datos del partido o, si faltan, con las estadísticas de Bzzoiro. Si pasados
     * tres días siguen faltando datos, la apuesta se anula y se devuelve el importe.
     *
     * @return número de apuestas liquidadas
     */
    @Transactional
    public int settlePending() {
        int settled = 0;
        Instant now = Instant.now();
        for (Bet bet : bets.findByStatus(BetStatus.PENDING)) {
            if (bet.getKickoff().plus(SETTLE_AFTER_KICKOFF).isAfter(now)) {
                continue;
            }
            Optional<BetStatus> outcome = outcomeOf(bet.getEventId(), bet.getHomeTeam(), bet.getAwayTeam(),
                    bet.getKickoff(), bet.getMarket(), bet.getSelection(), bet.getLine());
            if (outcome.isEmpty()) {
                if (bet.getKickoff().plus(VOID_AFTER).isBefore(now)) {
                    close(bet, BetStatus.VOID);
                    settled++;
                }
                continue;
            }
            close(bet, outcome.get());
            settled++;
        }
        return settled + settleParlays(now);
    }

    /**
     * Resuelve las selecciones pendientes de cada combinada cuyo partido terminó (anulándolas tras tres días sin
     * datos) y cierra la combinada en cuanto se sabe el resultado: una selección perdida basta para perderla.
     */
    private int settleParlays(Instant now) {
        int settled = 0;
        for (Parlay parlay : parlays.findByStatus(BetStatus.PENDING)) {
            for (ParlayLeg leg : parlay.getLegs()) {
                if (leg.getStatus() != BetStatus.PENDING || leg.getKickoff().plus(SETTLE_AFTER_KICKOFF).isAfter(now)) {
                    continue;
                }
                Optional<BetStatus> outcome = outcomeOf(leg.getEventId(), leg.getHomeTeam(), leg.getAwayTeam(),
                        leg.getKickoff(), leg.getMarket(), leg.getSelection(), leg.getLine());
                if (outcome.isPresent()) {
                    leg.setStatus(outcome.get());
                } else if (leg.getKickoff().plus(VOID_AFTER).isBefore(now)) {
                    leg.setStatus(BetStatus.VOID);
                }
            }
            ParlaySettlement.Result result = ParlaySettlement.resolve(
                    parlay.getLegs().stream().map(ParlayLeg::getStatus).toList(),
                    parlay.getLegs().stream().map(ParlayLeg::getOdds).toList());
            if (result.status() == BetStatus.PENDING) {
                parlays.save(parlay);
                continue;
            }
            long payout = switch (result.status()) {
                case WON -> Math.round(parlay.getStake() * result.effectiveOdds());
                case VOID -> parlay.getStake();
                default -> 0L;
            };
            parlay.setStatus(result.status());
            parlay.setPayout(payout);
            parlay.setSettledAt(now);
            parlays.save(parlay);
            if (payout > 0) {
                User user = users.findByIdForUpdate(parlay.getUserId()).orElseThrow();
                user.setBalance(user.getBalance() + payout);
                users.save(user);
            }
            settled++;
        }
        return settled;
    }

    /** Resultado de una selección con los datos guardados del partido; vacío si aún no hay datos para decidirla. */
    private Optional<BetStatus> outcomeOf(long eventId, String homeTeam, String awayTeam, Instant kickoff, Market market,
            String selection, Double line) {
        Optional<Match> found = findMatch(homeTeam, awayTeam, kickoff);
        if (found.isEmpty() || found.get().getFullTimeHomeGoals() == null || found.get().getFullTimeAwayGoals() == null) {
            return Optional.empty();
        }
        Match match = found.get();
        Integer corners = match.getCorners();
        Integer cards = match.getYellowCards() == null ? null
                : match.getYellowCards() + (match.getRedCards() == null ? 0 : match.getRedCards());
        if ((market == Market.CORNERS && corners == null) || (market == Market.CARDS && cards == null)) {
            try {
                Integer[] totals = bzzoiro.matchTotals(eventId);
                corners = corners != null ? corners : totals[0];
                cards = cards != null ? cards : totals[1];
            } catch (RuntimeException ex) {
                log.debug("Sin estadisticas para el evento {}: {}", eventId, ex.getMessage());
            }
        }
        return BetSettlement.outcome(market, selection, line, match.getFullTimeHomeGoals(), match.getFullTimeAwayGoals(),
                corners, cards);
    }

    private Optional<Match> findMatch(String homeTeam, String awayTeam, Instant kickoff) {
        Optional<Team> home = teamRepository.findByNameIgnoreCase(TeamNameNormalizer.canonical(homeTeam));
        Optional<Team> away = teamRepository.findByNameIgnoreCase(TeamNameNormalizer.canonical(awayTeam));
        if (home.isEmpty() || away.isEmpty()) {
            return Optional.empty();
        }
        LocalDate day = kickoff.atZone(ZoneId.of("Europe/Madrid")).toLocalDate();
        return matchRepository.findFirstByHomeTeamIdAndAwayTeamIdAndMatchDateBetween(home.get().getId(),
                away.get().getId(), day.minusDays(1).atStartOfDay(), day.plusDays(1).atTime(java.time.LocalTime.MAX));
    }

    private void close(Bet bet, BetStatus status) {
        long payout = switch (status) {
            case WON -> Math.round(bet.getStake() * bet.getOdds());
            case VOID -> bet.getStake();
            default -> 0L;
        };
        bet.setStatus(status);
        bet.setPayout(payout);
        bet.setSettledAt(Instant.now());
        bets.save(bet);
        if (payout > 0) {
            User user = users.findByIdForUpdate(bet.getUserId()).orElseThrow();
            user.setBalance(user.getBalance() + payout);
            users.save(user);
        }
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    /**
     * Probabilidad implicita de la cuota sin el margen de las casas: se normalizan las cuotas del mismo mercado
     * y linea (1X2 completo, o el par mas/menos). Nula si el grupo esta incompleto.
     */
    static Double noVigProbability(OddsLine target, List<OddsLine> lines) {
        List<OddsLine> group = lines.stream().filter(l -> l.market() == target.market() && l.isReal()
                && java.util.Objects.equals(l.line(), target.line())).toList();
        int expected = target.market() == Market.WINNER ? 3 : 2;
        if (group.size() != expected) {
            return null;
        }
        double total = group.stream().mapToDouble(l -> 1.0 / l.odds()).sum();
        return (1.0 / target.odds()) / total;
    }

    private BoardMatch buildBoard(OddsEvent event) {
        Optional<Team> home = teamRepository.findByNameIgnoreCase(TeamNameNormalizer.canonical(event.homeTeam()));
        Optional<Team> away = teamRepository.findByNameIgnoreCase(TeamNameNormalizer.canonical(event.awayTeam()));
        String homeName = home.map(Team::getName).orElse(event.homeTeam());
        String awayName = away.map(Team::getName).orElse(event.awayTeam());

        MarketModel model = null;
        if (home.isPresent() && away.isPresent() && !home.get().getId().equals(away.get().getId())) {
            try {
                PredictionResponseDto prediction = facade.predict(home.get().getId(), away.get().getId());
                model = new MarketModel(prediction);
            } catch (RuntimeException ex) {
                log.debug("Sin prediccion para {} - {}: {}", homeName, awayName, ex.getMessage());
            }
        }

        List<OddsLine> lines = new ArrayList<>(oddsProvider.odds(event.id()));
        List<OddsLine> realCards = cardsProvider.cardOdds(event.homeTeam(), event.awayTeam(), event.kickoff());
        if (!realCards.isEmpty()) {
            lines.addAll(realCards);
        } else if (model != null) {
            lines.addAll(model.demoCardOdds());
        }
        List<Offer> offers = new ArrayList<>();
        for (OddsLine line : lines) {
            Double probability = model == null ? null
                    : model.probability(line.market(), line.selection(), line.line()).orElse(null);
            Double market = line.isReal() ? noVigProbability(line, lines) : null;
            Double adjusted = probability == null ? null : market == null ? probability : 0.5 * probability + 0.5 * market;
            Double edge = adjusted == null ? null : round4(adjusted * line.odds() - 1.0);
            offers.add(new Offer(line, probability == null ? null : round4(probability), market == null ? null : round4(market),
                    adjusted == null ? null : round4(adjusted), edge));
        }
        return new BoardMatch(event, home.map(Team::getId).orElse(null), away.map(Team::getId).orElse(null), homeName,
                awayName, model != null, offers);
    }
}
