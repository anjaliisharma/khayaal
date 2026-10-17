package com.khayaal.medicines;

import com.khayaal.schedule.ScheduleRule;
import com.khayaal.schedule.ScheduleType;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SchedulePersistence {
    private SchedulePersistence() { }
    public static ScheduleRule loadRule(JdbcClient jdbc,UUID ruleId,LocalDate startDate,LocalDate endDate,String type,Integer interval,LocalDate anchor){
        List<LocalTime> times=jdbc.sql("select local_time from schedule_rule_times where schedule_rule_id=:id order by local_time").param("id",ruleId).query(LocalTime.class).list();
        Set<DayOfWeek> days=jdbc.sql("select day_of_week from schedule_rule_days where schedule_rule_id=:id").param("id",ruleId).query(Integer.class).list().stream().map(DayOfWeek::of).collect(Collectors.toSet());
        return new ScheduleRule(ScheduleType.valueOf(type),startDate,endDate,days,interval==null?0:interval,anchor,times);
    }
}
