package dev.pppp.controller;

import dev.pppp.model.Cartridge;
import dev.pppp.service.CartridgeService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/cartridges")
public class CartridgeController {
    private final CartridgeService cartridges;

    public CartridgeController(CartridgeService cartridges) {
        this.cartridges = cartridges;
    }

    @GetMapping
    public List<Cartridge> list() { return cartridges.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cartridge upload(@RequestParam("file") MultipartFile file) throws IOException {
        return cartridges.upload(file.getOriginalFilename(), file.getBytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { cartridges.delete(id); }
}
