package com.siemens.internship;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private ItemRepository itemRepository;

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }


    @Async
    public CompletableFuture<List<Item>> processItemsAsync() {

        List<Long> itemIds = itemRepository.findAllIds();

        // Thread-safe collection to store processed items
        ConcurrentLinkedQueue<Item> processedItems = new ConcurrentLinkedQueue<>();

        // Create async tasks for each item
        List<CompletableFuture<Void>> futures = itemIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(100);

                        Item item = itemRepository.findById(id).orElse(null);
                        if (item == null) {
                            return;
                        }

                        item.setStatus("PROCESSED");
                        itemRepository.save(item);
                        processedItems.add(item);

                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted thread", e);
                    }
                    catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }))
                .collect(Collectors.toList());

        // Combine all tasks and return when completed
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> List.copyOf(processedItems))
                .exceptionally(exception -> {
                    System.err.println("Error processing items: " + exception.getMessage());
                    return List.of(); // Return empty list if error occurred
                });
    }

}

