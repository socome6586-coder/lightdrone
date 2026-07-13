package com.lightdrone.controller;

import com.lightdrone.service.DroneServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceController {

    private final DroneServiceService droneServiceService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("services", droneServiceService.getVisibleServices());
        return "services";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("service", droneServiceService.getService(id));
        model.addAttribute("services", droneServiceService.getVisibleServices());
        return "service-detail";
    }
}
