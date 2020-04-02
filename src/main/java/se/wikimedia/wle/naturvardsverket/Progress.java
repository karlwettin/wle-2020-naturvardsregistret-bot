package se.wikimedia.wle.naturvardsverket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

@Data
public class Progress {

  public static void main(String[] args) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();

    Progress progress = new Progress();
    Entity entity = new Entity();
    entity.setNvrid("123");
    entity.setWikidataIdentity("Q1234");
    entity.setEpochStarted(System.currentTimeMillis());
    progress.add(entity);

    String json = objectMapper.writeValueAsString(progress);
    Progress progress2 = objectMapper.readValue(json, Progress.class);
    System.currentTimeMillis();

  }

  public void add(Entity entity) {
    processed.put(entity.getNvrid(), entity);
  }

  private Map<String, Entity> processed = new HashMap<>();

  @Data
  public static class Entity {
    private String nvrid;

    private String wikidataIdentity;

    private boolean skipped;

    private Long epochStarted;
    private Long epochEnded;

    private boolean createdWikidata;
    private boolean updatedWikidata;

    private List<String> createdClaims = new ArrayList<>();
    private List<String> deletedClaims = new ArrayList<>();

    private boolean createdCommonsGeoshape;
    private boolean updatedCommonsGeoshape;

    private String error;

  }

}