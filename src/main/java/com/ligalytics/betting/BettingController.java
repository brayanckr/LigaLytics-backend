package com.ligalytics.betting;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ligalytics.auth.AuthInterceptor;
import com.ligalytics.auth.User;
import com.ligalytics.auth.UserRepository;
import com.ligalytics.betting.BettingService.BoardMatch;
import com.ligalytics.betting.BettingService.Offer;
import com.ligalytics.betting.BettingService.Recommendation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API de la demostración de apuestas. <b>Todo el dinero es ficticio</b>: simulador educativo, sin pagos ni
 * conexión con casas de apuestas. Requiere iniciar sesión.
 */
@RestController
@RequestMapping(value = "/betting", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Apuestas (demo)", description = "Simulador educativo con saldo ficticio")
public class BettingController {

    public record OfferDto(String market, String marketLabel, String selection, Double line, double odds, String source,
            Double modelProbability, Double marketProbability, Double adjustedProbability, Double edge,
            boolean recommended) {
        static OfferDto of(Offer offer) {
            OddsLine l = offer.line();
            return new OfferDto(l.market().name(), l.market().label(), l.selection(), l.line(), l.odds(), l.source(),
                    offer.modelProbability(), offer.marketProbability(), offer.adjustedProbability(), offer.edge(),
                    BettingService.isRecommendable(offer));
        }
    }

    public record BettingMatchDto(long eventId, Instant kickoff, String homeTeam, String awayTeam, boolean hasModel,
            List<OfferDto> offers) {
        static BettingMatchDto of(BoardMatch match) {
            return new BettingMatchDto(match.event().id(), match.event().kickoff(), match.homeName(), match.awayName(),
                    match.hasModel(), match.offers().stream().map(OfferDto::of).toList());
        }
    }

    public record RecommendationDto(long eventId, Instant kickoff, String homeTeam, String awayTeam, OfferDto offer,
            long suggestedStake) {
        static RecommendationDto of(Recommendation r) {
            return new RecommendationDto(r.match().event().id(), r.match().event().kickoff(), r.match().homeName(),
                    r.match().awayName(), OfferDto.of(r.offer()), r.suggestedStake());
        }
    }

    public record BetDto(Long id, long eventId, String homeTeam, String awayTeam, Instant kickoff, String market,
            String marketLabel, String selection, Double line, double odds, String oddsSource, long stake, String status,
            long payout, Instant placedAt, Instant settledAt) {
        static BetDto of(Bet b) {
            return new BetDto(b.getId(), b.getEventId(), b.getHomeTeam(), b.getAwayTeam(), b.getKickoff(),
                    b.getMarket().name(), b.getMarket().label(), b.getSelection(), b.getLine(), b.getOdds(),
                    b.getOddsSource(), b.getStake(), b.getStatus().name(), b.getPayout(), b.getPlacedAt(),
                    b.getSettledAt());
        }
    }

    public record PlaceBetRequest(@NotNull Long eventId, @NotBlank String market, @NotBlank String selection, Double line,
            @NotNull Long stake) {
    }

    public record WalletDto(long balance, long initialBalance, long inPlay, long profit, String currency, String notice) {
    }

    private static final String NOTICE = "Simulación educativa con dinero ficticio. No se apuesta dinero real.";

    private final BettingService service;
    private final UserRepository users;

    public BettingController(BettingService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/matches")
    @Operation(summary = "Próximos partidos con cuotas, probabilidad del modelo y ventaja")
    public List<BettingMatchDto> matches(@RequestParam(name = "days", defaultValue = "7") int days) {
        requireOdds();
        return service.board(days).stream().map(BettingMatchDto::of).toList();
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Selecciones con ventaja según el modelo (no demostrada), con importe sugerido")
    public List<RecommendationDto> recommendations(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user) {
        requireOdds();
        return service.recommendations(user).stream().map(RecommendationDto::of).toList();
    }

    @PostMapping("/bets")
    @Operation(summary = "Hace una apuesta con saldo ficticio (la cuota la fija el servidor)")
    public BetDto place(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user,
            @Valid @RequestBody PlaceBetRequest request) {
        requireOdds();
        Market market;
        try {
            market = Market.valueOf(request.market().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Mercado no válido: " + request.market());
        }
        return BetDto.of(service.placeBet(user.getId(), request.eventId(), market, request.selection().trim(),
                request.line(), request.stake()));
    }

    @GetMapping("/bets")
    @Operation(summary = "Historial de apuestas de la cuenta")
    public List<BetDto> bets(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user) {
        return service.betsOf(user.getId()).stream().map(BetDto::of).toList();
    }

    @GetMapping("/wallet")
    @Operation(summary = "Saldo ficticio, dinero en juego y ganancia o pérdida")
    public WalletDto wallet(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user) {
        return walletOf(user.getId());
    }

    @PostMapping("/wallet/reset")
    @Operation(summary = "Reinicia el saldo a 100 000 COP ficticios y borra las apuestas")
    public WalletDto reset(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user) {
        service.resetWallet(user.getId());
        return walletOf(user.getId());
    }

    private WalletDto walletOf(Long userId) {
        long balance = users.findById(userId).map(User::getBalance).orElse(0L);
        long inPlay = service.betsOf(userId).stream().filter(b -> b.getStatus() == BetStatus.PENDING)
                .mapToLong(Bet::getStake).sum();
        return new WalletDto(balance, User.INITIAL_BALANCE, inPlay, balance + inPlay - User.INITIAL_BALANCE, "COP (ficticio)",
                NOTICE);
    }

    private void requireOdds() {
        if (!service.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cuotas no configuradas: define BZZOIRO_API_KEY");
        }
    }
}
