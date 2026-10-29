package com.example.itmohighloadsystemshw1.items;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void createsReadsAndUpdatesItem() throws Exception {
        String location = mvc.perform(post("/items").contentType(APPLICATION_JSON).content("""
                        {"name": "book", "description": "paper"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("book"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("paper"));

        mvc.perform(put(location).contentType(APPLICATION_JSON).content("""
                        {"name": "pen", "description": "blue"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pen"));

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("blue"));
    }

    @Test
    void unknownItemIsNotFound() throws Exception {
        mvc.perform(get("/items/{id}", 1_000_000_000)).andExpect(status().isNotFound());
        mvc.perform(put("/items/{id}", 1_000_000_000).contentType(APPLICATION_JSON).content("""
                        {"name": "ghost"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        mvc.perform(post("/items").contentType(APPLICATION_JSON).content("""
                        {"name": " "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
