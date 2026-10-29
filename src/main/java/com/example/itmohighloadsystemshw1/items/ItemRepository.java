package com.example.itmohighloadsystemshw1.items;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ItemRepository {

    private final ConcurrentMap<Long, Item> items = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Item create(String name, String description) {
        Item item = new Item(sequence.incrementAndGet(), name, description);
        items.put(item.id(), item);
        return item;
    }

    public Optional<Item> findById(long id) {
        return Optional.ofNullable(items.get(id));
    }

    public Optional<Item> update(long id, String name, String description) {
        return Optional.ofNullable(items.computeIfPresent(id, (key, old) -> new Item(key, name, description)));
    }
}
