package com.shici.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shici.service.GameCompanionVoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameCompanionVoiceControllerTest {
    private GameCompanionVoiceService voice;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        voice = mock(GameCompanionVoiceService.class);
        when(voice.isConfigured()).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new GameCompanionVoiceController(voice)).build();
    }

    @Test
    void rejectsInvalidSpeechBeforeCallingProvider() throws Exception {
        for (String text : new String[]{"", " ", "诗".repeat(2001)}) {
            mvc.perform(post("/api/game/companion/voice/speech").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(java.util.Map.of("text", text))))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(voice);
    }

    @Test
    void returnsAudioWithoutCachingOrChangingText() throws Exception {
        byte[] audio = new byte[]{73, 68, 51, 1, 2, 3};
        when(voice.speak("静夜思")).thenReturn(audio);
        mvc.perform(post("/api/game/companion/voice/speech").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"静夜思\"}"))
                .andExpect(status().isOk()).andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(audio));
        verify(voice).speak("静夜思");
    }

    @Test
    void rejectsOversizedAndEmptyRecordings() throws Exception {
        for (int size : new int[]{4, 1_000_001}) {
            mvc.perform(post("/api/game/companion/voice/transcribe").contentType("audio/wav").content(new byte[size]))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(voice);
    }

    @Test
    void forwardsWavAndReturnsOnlyRecognitionResult() throws Exception {
        byte[] wav = new byte[100];
        when(voice.transcribe(any())).thenReturn(mapper.readTree("{\"text\":\"明月\",\"recognition_ms\":30}"));
        mvc.perform(post("/api/game/companion/voice/transcribe").contentType("audio/wav").content(wav))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("明月"))
                .andExpect(jsonPath("$.data.recognitionMs").value(30))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(voice).transcribe(wav);
    }

    @Test
    void reportsVoiceOutageWithoutLeakingUpstreamDetails() throws Exception {
        when(voice.speak(anyString())).thenThrow(new IOException("private-upstream-key"));
        String response = mvc.perform(post("/api/game/companion/voice/speech").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private-upstream-key"));
    }

    @Test
    void reportsUnavailableRecognizerAndReadyStatus() throws Exception {
        when(voice.status()).thenThrow(new IOException("unavailable"));
        mvc.perform(get("/api/game/companion/voice/status")).andExpect(jsonPath("$.code").value(1));
        doReturn(mapper.readTree("{\"status\":\"ok\",\"asr\":{\"ready\":true,\"model\":\"SenseVoiceSmall-int8\"},\"voice\":\"zh-CN-XiaoxiaoNeural\"}")).when(voice).status();
        mvc.perform(get("/api/game/companion/voice/status")).andExpect(jsonPath("$.data.asrReady").value(true));
    }

    @Test
    void missingConfigurationExplainsNewComputerSetupWithoutCallingUpstream() throws Exception {
        when(voice.isConfigured()).thenReturn(false);
        mvc.perform(get("/api/game/companion/voice/status"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("setup.ps1")));
        verify(voice, never()).status();
    }
}
