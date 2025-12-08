package com.example.smarttrash.controller;

import com.example.smarttrash.model.LiveSnapshot;
import com.example.smarttrash.service.LiveDataService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class LiveController {

    private final LiveDataService liveDataService;

    public LiveController(LiveDataService liveDataService) {
        this.liveDataService = liveDataService;
    }

    @GetMapping("/live")
    public LiveSnapshot getLiveSnapshot() {
        return liveDataService.getSnapshot();
    }
}

