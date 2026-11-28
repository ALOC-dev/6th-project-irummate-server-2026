package com.irummate.domain.calendar.dto;

import com.irummate.domain.calendar.entity.CalendarEvent.Category;

import java.util.List;
import java.util.Optional;

public class CalendarExtractionOutput {
    public List<Candidate> schedules;

    public static class Candidate {
        public String title;
        public Category category;
        public Optional<String> audience;
        public Optional<String> startDate;
        public Optional<String> startTime;
        public Optional<String> endDate;
        public Optional<String> endTime;
        public String evidence;
        public Optional<String> reviewReason;
    }
}
