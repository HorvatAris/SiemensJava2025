package com.siemens.internship;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(ItemController.class)
public class ItemControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemService itemService;
    @Autowired
    private ItemController itemController;

    @Test
    void getAllItems_shouldReturnOk() throws Exception {
        when(itemService.findAll()).thenReturn(List.of(new Item(1L, "item", "desc", "NEW", "a@b.com")));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    void createItem_shouldReturnCreated() throws Exception {
        Item item = new Item(null, "item", "desc", "NEW", "a@b.com");
        when(itemService.save(any())).thenReturn(item);

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "name": "item",
                          "description": "desc",
                          "status": "NEW",
                          "email": "a@b.com"
                        }
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void createItem_invalidEmail_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "name": "item",
                          "description": "desc",
                          "status": "NEW",
                          "email": "invalid-email"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getItemById_notFound_shouldReturnNotFound() throws Exception {
        when(itemService.findById(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/items/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_shouldReturnOk() throws Exception {
        Item updated = new Item(1L, "item", "desc", "UPDATED", "a@b.com");
        when(itemService.findById(1L)).thenReturn(Optional.of(updated));
        when(itemService.save(any())).thenReturn(updated);

        mockMvc.perform(put("/api/items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "name": "item",
                          "description": "desc",
                          "status": "UPDATED",
                          "email": "a@b.com"
                        }
                        """))
                .andExpect(status().isOk());
    }

    @Test
    void updateItem_shouldReturnBadRequest_whenValidationFails() {
        Item invalidItem = new Item(); // Empty or invalid item

        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(true);

        ResponseEntity<Item> response = itemController.updateItem(1L, invalidItem, result);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void updateItem_shouldReturnNotFound_whenItemDoesNotExist() {
        Item updated = new Item(1L, "name", "desc", "status", "a@b.com");
        when(itemService.findById(1L)).thenReturn(Optional.empty());

        BindingResult result = mock(BindingResult.class);
        when(result.hasErrors()).thenReturn(false);

        ResponseEntity<Item> response = itemController.updateItem(1L, updated, result);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteItem_shouldReturnNoContent() {
        doNothing().when(itemService).deleteById(1L);

        ResponseEntity<Void> response = itemController.deleteItem(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void processItems_shouldReturnServerError_onFailure() throws Exception {
        ItemService mockService = mock(ItemService.class);
        ItemController controller = new ItemController();
        ReflectionTestUtils.setField(controller, "itemService", mockService);

        when(mockService.processItemsAsync()).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("fail"))
        );

        CompletableFuture<ResponseEntity<List<Item>>> future = controller.processItems();

        ResponseEntity<List<Item>> response = future.get(5, TimeUnit.SECONDS);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

}
