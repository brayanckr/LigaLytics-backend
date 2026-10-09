package com.ligalytics.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ligalytics.etl.DataSeederService;
import com.ligalytics.etl.EtlService;
import com.ligalytics.etl.dto.EtlSummary;
import com.ligalytics.patterns.proxy.ExternalDataSourceException;
import com.ligalytics.service.dto.SeedReport;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtlService etlService;

    @MockBean
    private DataSeederService dataSeederService;

    private static EtlSummary summary(String season) {
        return new EtlSummary("football-data", "SP1-" + season, 380, 380, 380, 0, 0, "http://test/" + season);
    }

    @Test
    void loadsAllDefaultSeasonsWhenNoneSpecified() throws Exception {
        when(etlService.ingestFootballData(anyString(), eq("SP1")))
                .thenAnswer(invocation -> summary(invocation.getArgument(0)));

        mockMvc.perform(post("/admin/etl/football-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].season", is("2021")))
                .andExpect(jsonPath("$[5].season", is("2526")))
                .andExpect(jsonPath("$[0].success", is(true)))
                .andExpect(jsonPath("$[0].summary.created", is(380)));

        verify(etlService, times(6)).ingestFootballData(anyString(), eq("SP1"));
    }

    @Test
    void continuesWhenOneSeasonFails() throws Exception {
        when(etlService.ingestFootballData(eq("1819"), eq("SP1"))).thenReturn(summary("1819"));
        when(etlService.ingestFootballData(eq("2021"), eq("SP1")))
                .thenThrow(new ExternalDataSourceException("sin conexión"));

        mockMvc.perform(post("/admin/etl/football-data").param("seasons", "1819,2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].success", is(true)))
                .andExpect(jsonPath("$[1].success", is(false)))
                .andExpect(jsonPath("$[1].error", is("sin conexión")));
    }

    @Test
    void rejectsInvalidSeasonCode() throws Exception {
        mockMvc.perform(post("/admin/etl/football-data").param("seasons", "2025"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/etl/football-data").param("seasons", "../etc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidDivision() throws Exception {
        mockMvc.perform(post("/admin/etl/football-data").param("division", "SP1/../x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusReturnsCounters() throws Exception {
        mockMvc.perform(get("/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams", notNullValue()))
                .andExpect(jsonPath("$.matches", notNullValue()));
    }

    @Test
    void seedsLocalFixtures() throws Exception {
        when(dataSeederService.seed())
                .thenReturn(new SeedReport(Instant.now(), 20, 380, List.of()));

        mockMvc.perform(post("/admin/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams", is(20)))
                .andExpect(jsonPath("$.matches", is(380)));

        verify(dataSeederService).seed();
    }
}
