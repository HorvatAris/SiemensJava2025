package com.siemens.internship;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
public class ItemServiceTests {
    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void findAll_shouldReturnAllItems() {
        List<Item> items = List.of(new Item(1L, "item1", "desc", "NEW", "a@b.com"));
        when(itemRepository.findAll()).thenReturn(items);

        List<Item> result = itemService.findAll();

        assertEquals(1, result.size());
        verify(itemRepository).findAll();
    }

    @Test
    void findById_shouldReturnItemIfExists() {
        Item item = new Item(1L, "item", "desc", "NEW", "a@b.com");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        Optional<Item> result = itemService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(item, result.get());
    }

    @Test
    void save_shouldPersistItem() {
        Item item = new Item(null, "item", "desc", "NEW", "a@b.com");
        when(itemRepository.save(any(Item.class))).thenReturn(item);

        Item result = itemService.save(item);

        assertNotNull(result);
        verify(itemRepository).save(item);
    }

    @Test
    void deleteById_shouldCallRepository() {
        itemService.deleteById(1L);
        verify(itemRepository).deleteById(1L);
    }

    @Test
    void processItemsAsync_shouldReturnProcessedItems() throws Exception {
        List<Long> ids = List.of(1L, 2L);
        when(itemRepository.findAllIds()).thenReturn(ids);
        when(itemRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.of(new Item(invocation.getArgument(0), "name", "desc", "OLD", "a@b.com")));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompletableFuture<List<Item>> future = itemService.processItemsAsync();

        List<Item> processedItems = future.get(5, TimeUnit.SECONDS);
        assertEquals(2, processedItems.size());
        assertTrue(processedItems.stream().allMatch(i -> "PROCESSED".equals(i.getStatus())));
    }

    @Test
    void processItemsAsync_shouldSkipNullItems() throws Exception {
        List<Long> ids = List.of(1L);
        when(itemRepository.findAllIds()).thenReturn(ids);
        when(itemRepository.findById(1L)).thenReturn(Optional.empty()); // simulate not found

        CompletableFuture<List<Item>> future = itemService.processItemsAsync();

        List<Item> result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isEmpty()); // item was null, nothing processed
    }

    @Test
    void processItemsAsync_shouldHandleInterruptedException() throws Exception {
        List<Long> ids = List.of(1L);
        when(itemRepository.findAllIds()).thenReturn(ids);
        when(itemRepository.findById(1L)).thenAnswer(invocation -> {
            Thread.currentThread().interrupt(); // Simulate interruption
            throw new InterruptedException("Simulated interruption");
        });

        CompletableFuture<List<Item>> future = itemService.processItemsAsync();

        List<Item> result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isEmpty()); // because exceptionally block returns List.of()
    }

    @Test
    void processItemsAsync_shouldHandleRepositoryException() throws Exception {
        List<Long> ids = List.of(1L);
        when(itemRepository.findAllIds()).thenReturn(ids);
        when(itemRepository.findById(1L)).thenThrow(new RuntimeException("DB failure"));

        CompletableFuture<List<Item>> future = itemService.processItemsAsync();

        List<Item> result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isEmpty()); // error fallback returns empty list
    }

}
