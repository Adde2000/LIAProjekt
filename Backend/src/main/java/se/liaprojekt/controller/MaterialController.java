package se.liaprojekt.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.liaprojekt.dto.MaterialRequest;
import se.liaprojekt.dto.MaterialResponse;
import se.liaprojekt.service.MaterialService;

import java.util.List;

@RestController
@RequestMapping("/api/courses/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping("/section/{sectionId}")
    public ResponseEntity<List<MaterialResponse>> getBySection(@PathVariable Long sectionId) {
        return ResponseEntity.ok(materialService.getBySection(sectionId));
    }

    @PostMapping("/section/{sectionId}")
    public ResponseEntity<MaterialResponse> create(
            @PathVariable Long sectionId,
            @RequestBody MaterialRequest request) {

        return ResponseEntity.ok(materialService.create(sectionId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaterialResponse> update(
            @PathVariable Long id,
            @RequestBody MaterialRequest request) {

        return ResponseEntity.ok(materialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
