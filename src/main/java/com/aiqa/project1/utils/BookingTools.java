package com.aiqa.project1.utils;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

//@Component
//public class ToolsUtils {
//
//    private final ToolsUtils toolsUtils;
//
//    public ToolsUtils(BookingService bookingService) {
//        this.bookingService = bookingService;
//    }
//
//    @Tool
//    public Booking getBookingDetails(String bookingNumber, String customerName, String customerSurname) {
//        return bookingService.getBookingDetails(bookingNumber, customerName, customerSurname);
//    }
//
//    @Tool
//    public void cancelBooking(String bookingNumber, String customerName, String customerSurname) {
//        bookingService.cancelBooking(bookingNumber, customerName, customerSurname);
//    }
//}