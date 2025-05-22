package com.example.auth_service.schedule_service.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.example.auth_service.schedule_service.domain.Schedule;
import com.example.auth_service.schedule_service.dto.domain.FlexiblePlaceOption;

@Data
@NoArgsConstructor
public class FlexibleScheduleRequest {
    private List<Schedule> fixedSchedules;
    private List<FlexiblePlaceOption> flexibleOptions;
}
