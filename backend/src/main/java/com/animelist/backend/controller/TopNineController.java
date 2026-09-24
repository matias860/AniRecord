package com.animelist.backend.controller;

import com.animelist.backend.dto.topnine.CurrentTopNineResponse;
import com.animelist.backend.dto.topnine.PublishTopNineRequest;
import com.animelist.backend.dto.topnine.TopNineSubmissionResponse;
import com.animelist.backend.service.TopNineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/top-nine")
public class TopNineController {
    private final TopNineService service;

    public TopNineController(TopNineService service) {
        this.service = service;
    }

    @GetMapping
    public CurrentTopNineResponse current() {
        return service.current();
    }

    @PostMapping
    public TopNineSubmissionResponse publish(@Valid @RequestBody PublishTopNineRequest request) {
        return service.publish(request);
    }
}
