package com.khayaal.doses;

import com.khayaal.schedule.ScheduleExpander;
import com.khayaal.schedule.ScheduleRule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@EnableConfigurationProperties(DoseGenerationProperties.class)
public class DoseEventGenerator {
    private final JdbcClient jdbc; private final Clock clock; private final int horizonDays; private final ScheduleExpander expander=new ScheduleExpander();
    public DoseEventGenerator(JdbcClient jdbc,Clock clock,DoseGenerationProperties properties){this.jdbc=jdbc;this.clock=clock;this.horizonDays=properties.generationHorizonDays();}

    @Transactional
    public int generate(UUID medicineId,UUID ruleId,ScheduleRule rule,ZoneId zone,Instant startInclusive){
        Instant end=startInclusive.plus(horizonDays,ChronoUnit.DAYS); int inserted=0;
        for(var occurrence:expander.expand(rule,zone,startInclusive,end)){
            inserted += jdbc.sql("insert into dose_events(id,medicine_id,schedule_rule_id,scheduled_at,scheduled_local,status) " +
                            "values (:id,:medicine,:rule,:at,:local,'PENDING') on conflict (medicine_id,scheduled_local) do nothing")
                    .param("id",UUID.randomUUID()).param("medicine",medicineId).param("rule",ruleId)
                    .param("at",Timestamp.from(occurrence.scheduledAt())).param("local",occurrence.scheduledLocal()).update();
        }
        return inserted;
    }

    @Scheduled(cron="0 10 2 * * *",zone="UTC") @Transactional
    public void extendRollingHorizon(){
        Instant now=clock.instant();
        jdbc.sql("select s.id,s.medicine_id,r.timezone,m.start_date,m.end_date,s.rule_type,s.interval_days,s.anchor_date " +
                        "from schedule_rule_versions s join medicines m on m.id=s.medicine_id join care_recipients r on r.id=m.recipient_id " +
                        "where m.active=true and m.as_needed=false and s.effective_from<=:now and (s.effective_to is null or s.effective_to>:now)")
                .param("now",Timestamp.from(now)).query((rs,n)->new ActiveRule(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),
                        rs.getDate(4).toLocalDate(),rs.getDate(5)==null?null:rs.getDate(5).toLocalDate(),rs.getString(6),(Integer)rs.getObject(7),
                        rs.getDate(8)==null?null:rs.getDate(8).toLocalDate())).list()
                .forEach(active->generate(active.medicineId(),active.ruleId(),com.khayaal.medicines.SchedulePersistence.loadRule(jdbc,active.ruleId(),active.startDate(),active.endDate(),active.type(),active.intervalDays(),active.anchorDate()),ZoneId.of(active.zone()),now));
    }

    private record ActiveRule(UUID ruleId,UUID medicineId,String zone,java.time.LocalDate startDate,java.time.LocalDate endDate,String type,Integer intervalDays,java.time.LocalDate anchorDate) { }
}
