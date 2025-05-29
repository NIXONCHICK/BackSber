package back.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PersonAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class SimpleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testBasicEndpoint() throws Exception {
        mockMvc.perform(get("/api/test"))
                .andExpect(status().isNotFound());
    }
} 