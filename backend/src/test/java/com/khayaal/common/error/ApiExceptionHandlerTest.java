package com.khayaal.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ValidationController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void validationErrorsUseProblemDetails() throws Exception {
        mvc.perform(post("/test-validation").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @RestController
    public static class ValidationController {
        @PostMapping("/test-validation")
        public void validate(@Valid @RequestBody TestRequest request) { }
    }

    public record TestRequest(@NotBlank String value) { }
}

