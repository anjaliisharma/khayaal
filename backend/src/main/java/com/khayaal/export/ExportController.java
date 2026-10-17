package com.khayaal.export;

import com.khayaal.identity.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final JdbcClient jdbc;
    private final Clock clock;
    public ExportController(JdbcClient jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}

    @GetMapping
    ResponseEntity<?> export(@RequestParam(defaultValue="json") String format,Authentication authentication){
        UUID user=CurrentUser.require(authentication).userId();
        List<Map<String,Object>> households=jdbc.sql("select h.id,h.name,h.default_timezone,hm.role from households h join household_memberships hm on hm.household_id=h.id where hm.user_id=:user order by h.name")
                .param("user",user).query((rs,row)->map("id",rs.getObject(1),"name",rs.getString(2),"defaultTimezone",rs.getString(3),"role",rs.getString(4))).list();
        List<Map<String,Object>> people=jdbc.sql("select r.id,r.household_id,r.name,r.birth_year,r.timezone,r.notes,r.archived from care_recipients r join household_memberships hm on hm.household_id=r.household_id where hm.user_id=:user order by r.name")
                .param("user",user).query((rs,row)->map("id",rs.getObject(1),"householdId",rs.getObject(2),"name",rs.getString(3),"birthYear",rs.getObject(4),"timezone",rs.getString(5),"notes",rs.getString(6),"archived",rs.getBoolean(7))).list();
        List<Map<String,Object>> medicines=jdbc.sql("select m.id,m.recipient_id,m.name,m.strength,m.form,m.units_per_dose,m.instructions,m.start_date,m.end_date,m.active from medicines m join care_recipients r on r.id=m.recipient_id join household_memberships hm on hm.household_id=r.household_id where hm.user_id=:user order by m.name")
                .param("user",user).query((rs,row)->map("id",rs.getObject(1),"recipientId",rs.getObject(2),"name",rs.getString(3),"strength",rs.getString(4),"form",rs.getString(5),"unitsPerDose",rs.getBigDecimal(6),"instructions",rs.getString(7),"startDate",rs.getObject(8),"endDate",rs.getObject(9),"active",rs.getBoolean(10))).list();
        ExportView export=new ExportView(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),households,people,medicines);
        if("json".equalsIgnoreCase(format))return ResponseEntity.ok(export);
        if(!"csv".equalsIgnoreCase(format))throw new IllegalArgumentException("Export format must be json or csv");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=khayaal-export.csv").body(csv(export));
    }

    private Map<String,Object> map(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int index=0;index<values.length;index+=2)result.put((String)values[index],values[index+1]);return result;}
    private String csv(ExportView export){
        StringBuilder result=new StringBuilder("recordType,id,parentId,name,details\n");
        export.households().forEach(row->row(result,"household",row.get("id"),null,row.get("name"),"timezone="+row.get("defaultTimezone")+"; role="+row.get("role")));
        export.people().forEach(row->row(result,"person",row.get("id"),row.get("householdId"),row.get("name"),"timezone="+row.get("timezone")));
        export.medicines().forEach(row->row(result,"medicine",row.get("id"),row.get("recipientId"),row.get("name"),"strength="+row.get("strength")+"; form="+row.get("form")+"; unitsPerDose="+row.get("unitsPerDose")));
        return result.toString();
    }
    private void row(StringBuilder target,Object... values){for(int index=0;index<values.length;index++){if(index>0)target.append(',');target.append(escape(values[index]));}target.append('\n');}
    private String escape(Object value){String text=value==null?"":String.valueOf(value);return "\""+text.replace("\"","\"\"")+"\"";}
    record ExportView(OffsetDateTime exportedAt,List<Map<String,Object>> households,List<Map<String,Object>> people,List<Map<String,Object>> medicines){}
}
