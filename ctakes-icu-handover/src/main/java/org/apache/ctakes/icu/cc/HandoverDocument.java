package org.apache.ctakes.icu.cc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-serializable ICU handover document matching the agreed schema.
 */
public class HandoverDocument {

   public String documentId;
   public List<SectionDto> sections = new ArrayList<>();
   public BackgroundDto background = new BackgroundDto();
   public SituationDto situation = new SituationDto();
   public AssessmentDto assessment = new AssessmentDto();
   public SystemsDto systems = new SystemsDto();
   public AccessDto access = new AccessDto();
   public List<ImagingDto> imaging = new ArrayList<>();
   public PlanDto plan = new PlanDto();

   public static class SectionDto {
      public String id;
      public String preferredText;
      public String tagText;
      public int begin;
      public int end;
   }

   public static class ConceptDto {
      public String text;
      public int begin;
      public int end;
      public String type;
      public String cui;
      public String preferredText;
      public String codingScheme;
      public String code;
      public Integer polarity;
      public Integer uncertainty;
      public String subject;
      public Integer historyOf;
      public String sectionId;
      public List<String> locations;
   }

   public static class MedDto {
      public String text;
      public int begin;
      public int end;
      public String cui;
      public String codingScheme;
      public String code;
      public String preferredText;
      public String dose;
      public String strength;
      public String frequency;
      public String route;
      public String sectionId;
      public String drugClass;
   }

   public static class EventDto {
      public String text;
      public String type;
      public String cui;
      public String docTimeRel;
      public TimeDto time;
      public int begin;
      public int end;
   }

   public static class TimeDto {
      public String text;
      public String normalized;
   }

   public static class BackgroundDto {
      public List<ConceptDto> conditions = new ArrayList<>();
      public List<ConceptDto> priorProcedures = new ArrayList<>();
      public List<ConceptDto> functionalStatus = new ArrayList<>();
   }

   public static class SituationDto {
      public List<EventDto> events = new ArrayList<>();
   }

   public static class AssessmentDto {
      public List<ConceptDto> problems = new ArrayList<>();
   }

   public static class SystemsDto {
      public CnsDto cns = new CnsDto();
      public CvsDto cvs = new CvsDto();
      public RespiratoryDto respiratory = new RespiratoryDto();
      public GiDto gi = new GiDto();
      public GuDto gu = new GuDto();
      public HematologyDto hematology = new HematologyDto();
      public List<LabDto> labs = new ArrayList<>();
   }

   public static class CnsDto {
      public List<ConceptDto> findings = new ArrayList<>();
      public GcsDto gcs;
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class GcsDto {
      public Integer value;
      public Boolean underSedation;
      public String text;
      public int begin;
      public int end;
   }

   public static class CvsDto {
      public Map<String, String> status = new LinkedHashMap<>();
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class RespiratoryDto {
      public VentDto support;
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class VentDto {
      public String mode;
      public Map<String, Object> settings = new LinkedHashMap<>();
      public String o2Device;
      public String text;
      public int begin;
      public int end;
   }

   public static class GiDto {
      public FeedingDto feeding;
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class FeedingDto {
      public String route;
      public String formula;
      public String text;
      public int begin;
      public int end;
   }

   public static class GuDto {
      public UopDto urineOutput;
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class UopDto {
      public Float value;
      public String unit;
      public String text;
      public int begin;
      public int end;
   }

   public static class HematologyDto {
      public List<ConceptDto> coagStatus = new ArrayList<>();
      public List<MedDto> anticoagulants = new ArrayList<>();
      public List<CultureDto> cultures = new ArrayList<>();
   }

   public static class LabDto {
      public String name;
      public String value;
      public String unit;
      public String text;
      public String preferredText;
      public String cui;
      public String codingScheme;
      public String code;
      public int begin;
      public int end;
   }

   public static class CultureDto {
      public String site;
      public String organism;
      public String status;
      public String preferredText;
      public String cui;
      public String codingScheme;
      public String code;
      public List<String> sensitivities = new ArrayList<>();
      public String text;
      public int begin;
      public int end;
   }

   public static class AccessDto {
      public List<LineDto> lines = new ArrayList<>();
      public List<MedDto> antibiotics = new ArrayList<>();
      /** All medication mentions with drugClass / dose / route / frequency. */
      public List<MedDto> medications = new ArrayList<>();
   }

   public static class LineDto {
      public String type;
      public String site;
      public TimeDto insertDate;
      public String text;
      public String preferredText;
      public String cui;
      public String codingScheme;
      public String code;
      public int begin;
      public int end;
   }

   public static class ImagingDto {
      public String modality;
      public String bodySite;
      public TimeDto date;
      public List<ConceptDto> findings = new ArrayList<>();
      public String procedureText;
      public int begin;
      public int end;
   }

   public static class PlanDto {
      public String sectionText;
      public List<PlanItemDto> items = new ArrayList<>();
   }

   public static class PlanItemDto {
      public String text;
      public int begin;
      public int end;
   }
}
