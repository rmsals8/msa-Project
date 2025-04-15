package com.example.schedule_service.dto.request;

import com.example.schedule_service.domain.Schedule;
import com.example.schedule_service.dto.domain.FlexiblePlaceOption;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class FlexibleScheduleRequest {
    private List<Schedule> fixedSchedules;
    private List<FlexiblePlaceOption> flexibleOptions;
}
