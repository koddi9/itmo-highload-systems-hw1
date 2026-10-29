package com.example.itmohighloadsystemshw1.items;

import com.example.itmohighloadsystemshw1.chaos.FaultInjector;
import com.example.itmohighloadsystemshw1.monitoring.Monitored;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Monitored
@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository items;
    private final FaultInjector faults;

    public ItemController(ItemRepository items, FaultInjector faults) {
        this.items = items;
        this.faults = faults;
    }

    @PostMapping
    public ResponseEntity<Item> create(@RequestBody ItemRequest request) {
        faults.inject();
        validate(request);
        Item item = items.create(request.name(), request.description());
        return ResponseEntity.created(URI.create("/items/" + item.id())).body(item);
    }

    @GetMapping("/{id}")
    public Item get(@PathVariable long id) {
        faults.inject();
        return items.findById(id).orElseThrow(() -> notFound(id));
    }

    @PutMapping("/{id}")
    public Item update(@PathVariable long id, @RequestBody ItemRequest request) {
        faults.inject();
        validate(request);
        return items.update(id, request.name(), request.description()).orElseThrow(() -> notFound(id));
    }

    private static void validate(ItemRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name must not be blank");
        }
    }

    private static ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "item " + id + " not found");
    }
}
