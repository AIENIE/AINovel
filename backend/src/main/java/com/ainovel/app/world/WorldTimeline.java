package com.ainovel.app.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import static com.ainovel.app.world.WorldDraftDtos.*;

/** World dates are author-defined coordinates, not dates in the server's calendar or time zone. */
public final class WorldTimeline {
    public static final int SCHEMA_VERSION=1;
    private WorldTimeline() {}

    public static Timeline empty() { return new Timeline(SCHEMA_VERSION,"",List.of(),List.of()); }

    /** Logical emptiness remains null so pre-timeline snapshots keep their original serialized bytes. */
    public static Timeline normalize(Timeline value) {
        if(value==null)return null;
        if(value.schemaVersion()!=0&&value.schemaVersion()!=SCHEMA_VERSION)throw invalid("WORLD_TIMELINE_VERSION_INVALID");
        String calendar=text(value.calendarName()).strip();
        if(calendar.length()>255)throw invalid("WORLD_TIMELINE_CALENDAR_INVALID");
        var ids=new HashSet<UUID>();var eras=new ArrayList<Era>();
        for(var era:list(value.eras())) {
            if(era==null||era.id()==null||!ids.add(era.id()))throw invalid("WORLD_TIMELINE_ERA_INVALID");
            eras.add(new Era(era.id(),title(era.name(),"WORLD_TIMELINE_ERA_INVALID"),text(era.description()),normalizeDate(era.date()),era.sortOrder()));
        }
        var eraIds=Set.copyOf(ids);var events=new ArrayList<Event>();
        for(var event:list(value.events())) {
            if(event==null||event.id()==null||!ids.add(event.id()))throw invalid("WORLD_TIMELINE_EVENT_INVALID");
            var linkedEras=references(event.eraIds());
            if(!eraIds.containsAll(linkedEras))throw invalid("WORLD_TIMELINE_ERA_REFERENCE_INVALID");
            events.add(new Event(event.id(),title(event.title(),"WORLD_TIMELINE_EVENT_INVALID"),text(event.summary()),text(event.content()),
                strings(event.aliases()),strings(event.tags()),normalizeDate(event.date()),references(event.entryIds()),linkedEras,event.sortOrder()));
        }
        if(calendar.isEmpty()&&eras.isEmpty()&&events.isEmpty())return null;
        eras.sort(Comparator.comparing(era->era.id().toString()));
        events.sort(Comparator.comparing(event->event.id().toString()));
        return new Timeline(SCHEMA_VERSION,calendar,List.copyOf(eras),List.copyOf(events));
    }

    public static Timeline normalize(Timeline value,Set<UUID> entryIds) {
        var normalized=normalize(value);
        if(normalized!=null&&normalized.events().stream().anyMatch(event->!entryIds.containsAll(event.entryIds())))
            throw invalid("WORLD_TIMELINE_ENTRY_REFERENCE_INVALID");
        return normalized;
    }

    public static Timeline read(String value,ObjectMapper json) {
        if(value==null||value.isBlank()||value.equals("null"))return null;
        try{return normalize(json.readValue(value,Timeline.class));}
        catch(com.ainovel.app.common.ApiStatusException ex){throw ex;}
        catch(Exception ex){throw new IllegalStateException("WORLD_TIMELINE_DATA_INVALID",ex);}
    }

    /** Only references to targets removed in this save are cleared; invented references remain errors. */
    public static Timeline cleanupReferences(Timeline value,Set<UUID> deletedEntryIds,Set<UUID> deletedEraIds) {
        if(value==null)return null;
        var events=new ArrayList<Event>();
        for(var event:list(value.events())) {
            if(event==null)throw invalid("WORLD_TIMELINE_EVENT_INVALID");
            events.add(new Event(event.id(),event.title(),event.summary(),event.content(),event.aliases(),event.tags(),event.date(),
                list(event.entryIds()).stream().filter(id->id==null||!deletedEntryIds.contains(id)).toList(),
                list(event.eraIds()).stream().filter(id->id==null||!deletedEraIds.contains(id)).toList(),event.sortOrder()));
        }
        return new Timeline(value.schemaVersion(),value.calendarName(),value.eras(),events);
    }

    public static Set<UUID> eraIds(Timeline value) {
        return value==null?Set.of():list(value.eras()).stream().filter(Objects::nonNull).map(Era::id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public static DateRange normalizeDate(DateRange value) {
        if(value==null)return null;
        var start=normalizePoint(value.start());var end=normalizePoint(value.end());
        if(start==null&&end==null)return null;
        if(start!=null&&end!=null&&compareKnown(start,end)>0)throw invalid("WORLD_TIMELINE_DATE_RANGE_INVALID");
        return new DateRange(start,end);
    }

    private static DatePoint normalizePoint(DatePoint value) {
        if(value==null)return null;
        if(value.year()==null||value.month()!=null&&value.month()<=0||value.day()!=null&&(value.day()<=0||value.month()==null))
            throw invalid("WORLD_TIMELINE_DATE_INVALID");
        return value;
    }

    private static int compareKnown(DatePoint start,DatePoint end) {
        int result=Integer.compare(start.year(),end.year());
        if(result!=0||start.month()==null||end.month()==null)return result;
        result=Integer.compare(start.month(),end.month());
        return result!=0||start.day()==null||end.day()==null?result:Integer.compare(start.day(),end.day());
    }

    public static String renderDate(DateRange value) {
        var date=normalizeDate(value);if(date==null)return "日期未知";
        if(Objects.equals(date.start(),date.end()))return renderPoint(date.start());
        return renderPoint(date.start())+" — "+renderPoint(date.end());
    }

    private static String renderPoint(DatePoint point) {
        if(point==null)return "未知";
        return point.year()+"年"+(point.month()==null?"":point.month()+"月")+(point.day()==null?"":point.day()+"日");
    }

    private static List<UUID> references(List<UUID> value) {
        if(value==null)return List.of();
        if(value.stream().anyMatch(Objects::isNull))throw invalid("WORLD_TIMELINE_REFERENCE_INVALID");
        return value.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
    }
    private static List<String> strings(List<String> value) {
        return list(value).stream().filter(Objects::nonNull).map(String::strip).filter(s->!s.isEmpty()).distinct().toList();
    }
    private static String title(String value,String code) {
        String normalized=text(value).strip();if(normalized.isEmpty()||normalized.length()>255)throw invalid(code);return normalized;
    }
    private static String text(String value) { return Objects.toString(value,""); }
    private static <T>List<T> list(List<T> value) { return value==null?List.of():value; }
    private static com.ainovel.app.common.ApiStatusException invalid(String code) { return WorldEntryStore.error(code); }
}
