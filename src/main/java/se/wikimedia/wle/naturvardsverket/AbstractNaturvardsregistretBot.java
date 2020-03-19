package se.wikimedia.wle.naturvardsverket;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.helpers.ItemDocumentBuilder;
import org.wikidata.wdtk.datamodel.helpers.ReferenceBuilder;
import org.wikidata.wdtk.datamodel.helpers.StatementBuilder;
import org.wikidata.wdtk.datamodel.implementation.QuantityValueImpl;
import org.wikidata.wdtk.datamodel.implementation.StringValueImpl;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class AbstractNaturvardsregistretBot extends AbstractBot {

  private Logger log = LoggerFactory.getLogger(getClass());


  public AbstractNaturvardsregistretBot() {
    super("Naturvardsregistret_bot", "0.1");
  }

  private DateTimeFormatter featureValueDateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  public abstract String commonGeoshapeArticleNameFactory(NaturvardsregistretObject object);

  /**
   * Files to be processed
   */
  protected abstract File[] getNaturvardsregistretGeoJsonFiles();

  /**
   * Q describing e.g. nature reserve, national park, etc
   */
  protected abstract String getNaturvardsregistretObjectTypeEntityId();

  /**
   * E.g. https://metadatakatalogen.naturvardsverket.se/metadatakatalogen/GetMetaDataById?id=2921b01a-0baf-4702-a89f-9c5626c97844
   */
  protected abstract String getNaturvardsregistretObjectTypeSourceUrl();

  private EntityDocument naturvardsregistretEntityType;

  protected EntityDocument getNaturvardsregistretObjectTypeEntityDocument() throws MediaWikiApiErrorException, IOException {
    if (naturvardsregistretEntityType == null) {
      naturvardsregistretEntityType = getWikiData().getEntityDocument(getNaturvardsregistretObjectTypeEntityId(), true);
      if (naturvardsregistretEntityType == null) {
        naturvardsregistretEntityType = WikiData.NULL_ENTITY;
      }
    }
    return naturvardsregistretEntityType == WikiData.NULL_ENTITY ? null : naturvardsregistretEntityType;
  }

  @Override
  protected void execute() throws Exception {

    initializeWikiData();

    for (File file : getNaturvardsregistretGeoJsonFiles()) {
      log.info("Processing {}", file.getAbsolutePath());
      FeatureCollection featureCollection = getObjectMapper().readValue(file, FeatureCollection.class);

      log.info("Ensure that we are aware of all WikiData references");
      for (Feature feature : featureCollection.getFeatures()) {
        String operator = (String) feature.getProperties().get("FORVALTARE");
        if (operatorsByNvrProperty.get(operator) == null) {
          String operatorId = wikiData.findSingleObjectByUniqueLabel(operator, "sv");
          if (operatorId != null) {
            operatorsByNvrProperty.put(operator, getWikiData().getEntityIdValue(operatorId, true));
            log.warn("Operator '{}' was resolved using unique label at WikiData as {}", operator, operatorId);
          } else {
            log.error("Operator '{}' is an unknown WikiData object for us.", operator);
          }
        }
      }

      log.info("Processing entities...");
      for (Feature feature : featureCollection.getFeatures()) {
        // filter out null value properties
        feature.getProperties().entrySet().removeIf(property -> property.getValue() == null);
        // process
        process(feature);
      }
    }

  }

  private Map<String, EntityIdValue> iuncCategories = new HashMap<>();


  private Map<String, EntityIdValue> operatorsByNvrProperty = new HashMap<>();



  private void initializeWikiData() throws MediaWikiApiErrorException, IOException {
    log.info("Initializing WikiData properties...");

    getWikiData().getNamedEntities().put("instance of", getWikiData().getEntityIdValue("P31"));
    getWikiData().getNamedEntities().put("nature reserve", getWikiData().getEntityIdValue("Q179049"));

    getWikiData().getNamedEntities().put("inception date", getWikiData().getEntityIdValue("P571"));

    getWikiData().getNamedEntities().put("IUCN protected areas category", getWikiData().getEntityIdValue("P814"));


    // , Områden som ej kan klassificeras enligt IUCN: s system.
    iuncCategories.put("0", WikiData.NULL_ENTITY_VALUE);
    // , Strikt naturreservat (Strict Nature Reserve)
    iuncCategories.put("IA", getWikiData().getEntityIdValue("Q14545608"));
    // , Vildmarksområde (Wilderness Area)
    iuncCategories.put("IB", getWikiData().getEntityIdValue("Q14545620"));
    //, Nationalpark (National Park)
    iuncCategories.put("II", getWikiData().getEntityIdValue("Q14545628"));
    // , Naturmonument (Natural Monument)
    iuncCategories.put("III", getWikiData().getEntityIdValue("Q14545633"));
    //  Habitat/Artskyddsområde (Habitat/Species Management Area)
    iuncCategories.put("IV", getWikiData().getEntityIdValue("Q14545639"));
    // Skyddat landskap/havsområde (Protected Landscape/Seascape)
    iuncCategories.put("V", getWikiData().getEntityIdValue("Q14545646"));

    getWikiData().getNamedEntities().put("country", getWikiData().getEntityIdValue("P17"));
    getWikiData().getNamedEntities().put("Sweden", getWikiData().getEntityIdValue("Q34"));

    getWikiData().getNamedEntities().put("located in the administrative territorial entity", getWikiData().getEntityIdValue("P131"));


    getWikiData().getNamedEntities().put("coordinate location", getWikiData().getEntityIdValue("P625"));

    getWikiData().getNamedEntities().put("geoshape", getWikiData().getEntityIdValue("P3896"));

    getWikiData().getNamedEntities().put("operator", getWikiData().getEntityIdValue("P137"));

    getWikiData().getNamedEntities().put("hectare", getWikiData().getEntityIdValue("Q35852"));
    getWikiData().getNamedEntities().put("area", getWikiData().getEntityIdValue("P2046"));
    getWikiData().getNamedEntities().put("applies to part", getWikiData().getEntityIdValue("P518"));
    getWikiData().getNamedEntities().put("forest", getWikiData().getEntityIdValue("Q4421"));
    getWikiData().getNamedEntities().put("land", getWikiData().getEntityIdValue("Q11081619"));
    getWikiData().getNamedEntities().put("body of water", getWikiData().getEntityIdValue("Q15324"));

    getWikiData().getNamedEntities().put("nvrid", getWikiData().getEntityIdValue("P3613"));
    getWikiData().getNamedEntities().put("wdpaid", getWikiData().getEntityIdValue("P809"));

    getWikiData().getNamedEntities().put("reference URL", getWikiData().getEntityIdValue("P854"));
    getWikiData().getNamedEntities().put("stated in", getWikiData().getEntityIdValue("P248"));
    getWikiData().getNamedEntities().put("Protected Areas (Nature Reserves)", getWikiData().getEntityIdValue("Q29580583"));
    getWikiData().getNamedEntities().put("retrieved", getWikiData().getEntityIdValue("P813"));
    getWikiData().getNamedEntities().put("publication date", getWikiData().getEntityIdValue("P577"));



      log.info("Loading operators...");
      {
        ArrayNode forvaltare = getObjectMapper().readValue(new File("data/forvaltare.json"), ArrayNode.class);
        for (int i = 0; i < forvaltare.size(); i++) {
          operatorsByNvrProperty.put(forvaltare.get(i).get("sv").textValue(), wikiData.getEntityIdValue(forvaltare.get(i).get("item").textValue()));
        }
      }
      {
        ArrayNode municipality = getObjectMapper().readValue(new File("data/municipalities.json"), ArrayNode.class);
        for (int i = 0; i < municipality.size(); i++) {
          operatorsByNvrProperty.put(municipality.get(i).get("sv").textValue(), wikiData.getEntityIdValue(municipality.get(i).get("item").textValue()));
        }
      }


  }

  private void process(Feature feature) throws Exception {

    log.info("Processing {}", (String) feature.getProperty("NVRID"));

    // create object
    NaturvardsregistretObject naturvardsregistretObject = new NaturvardsregistretObject();
    naturvardsregistretObject.setFeature(feature);

    naturvardsregistretObject.setPublishedDate(LocalDate.parse("2020-02-25"));
    naturvardsregistretObject.setRetrievedDate(LocalDate.parse("2020-02-25"));

    naturvardsregistretObject.setNvrid(feature.getProperty("NVRID"));
    naturvardsregistretObject.setName(feature.getProperty("NAMN"));

    if (naturvardsregistretObject.getNvrid() == null) {
      log.warn("NVRID property missing in feature {}", getObjectMapper().writeValueAsString(feature));
      return;
    }


/*
    ███████╗██╗███╗   ██╗██████╗      ██████╗ ██████╗      ██████╗██████╗ ███████╗ █████╗ ████████╗███████╗
    ██╔════╝██║████╗  ██║██╔══██╗    ██╔═══██╗██╔══██╗    ██╔════╝██╔══██╗██╔════╝██╔══██╗╚══██╔══╝██╔════╝
    █████╗  ██║██╔██╗ ██║██║  ██║    ██║   ██║██████╔╝    ██║     ██████╔╝█████╗  ███████║   ██║   █████╗
    ██╔══╝  ██║██║╚██╗██║██║  ██║    ██║   ██║██╔══██╗    ██║     ██╔══██╗██╔══╝  ██╔══██║   ██║   ██╔══╝
    ██║     ██║██║ ╚████║██████╔╝    ╚██████╔╝██║  ██║    ╚██████╗██║  ██║███████╗██║  ██║   ██║   ███████╗
    ╚═╝     ╚═╝╚═╝  ╚═══╝╚═════╝      ╚═════╝ ╚═╝  ╚═╝     ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝   ╚═╝   ╚══════╝

    ██╗    ██╗██╗██╗  ██╗██╗██████╗  █████╗ ████████╗ █████╗     ██╗████████╗███████╗███╗   ███╗
    ██║    ██║██║██║ ██╔╝██║██╔══██╗██╔══██╗╚══██╔══╝██╔══██╗    ██║╚══██╔══╝██╔════╝████╗ ████║
    ██║ █╗ ██║██║█████╔╝ ██║██║  ██║███████║   ██║   ███████║    ██║   ██║   █████╗  ██╔████╔██║
    ██║███╗██║██║██╔═██╗ ██║██║  ██║██╔══██║   ██║   ██╔══██║    ██║   ██║   ██╔══╝  ██║╚██╔╝██║
    ╚███╔███╔╝██║██║  ██╗██║██████╔╝██║  ██║   ██║   ██║  ██║    ██║   ██║   ███████╗██║ ╚═╝ ██║
     ╚══╝╚══╝ ╚═╝╚═╝  ╚═╝╚═╝╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝    ╚═╝   ╚═╝   ╚══════╝╚═╝     ╚═╝
*/

    log.debug("Find unique WikiData item matching (instance of Naturvårdsregistret object type && Naturvårdsregistret object id).");
    String sparqlQuery = "SELECT ?item WHERE { ?item wdt:P3613 ?value. ?item wdt:P31 wd:" + getNaturvardsregistretObjectTypeEntityDocument().getEntityId().getId() + ". FILTER (?value IN (\""
        + naturvardsregistretObject.getNvrid() + "\")) SERVICE wikibase:label { bd:serviceParam wikibase:language \"[AUTO_LANGUAGE],en\". }} LIMIT 2";

    naturvardsregistretObject.setWikiDataObjectKey(wikiData.getSingleObject(sparqlQuery));
    ;
    if (naturvardsregistretObject.getWikiDataObjectKey() == null) {
      log.debug("Creating new WikiData item as there is none describing nvrid {}", naturvardsregistretObject.getNvrid());
      ItemDocumentBuilder builder = ItemDocumentBuilder.forItemId(ItemIdValue.NULL);
      builder.withStatement(
          addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
              .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("instance of"))
              .withValue(getWikiData().entity("nature reserve"))
              .withReference(naturvardsregistretReferenceFactory(naturvardsregistretObject))
          ).build());
      builder.withStatement(
          addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
              .forSubjectAndProperty(ItemIdValue.NULL,
                  getWikiData().property("nvrid"))
              .withValue(new StringValueImpl(naturvardsregistretObject.getNvrid()))
              .withReference(naturvardsregistretReferenceFactory(naturvardsregistretObject))
          ).build());
      if (!isDryRun()) {
        naturvardsregistretObject.setWikiDataItem(getWikiData().getDataEditor().createItemDocument(
            builder.build(),
            "Created by bot from data supplied by Naturvårdsverket",
            null
        ));
        log.info("Committed new fairly empty item {} to WikiData", naturvardsregistretObject.getWikiDataItem().getEntityId().getId());
      } else {
        // todo this might cause errors
        naturvardsregistretObject.setWikiDataItem(builder.build());
      }

    } else {
      log.debug("WikiData item {} is describing nvrid {}", naturvardsregistretObject.getWikiDataObjectKey(), naturvardsregistretObject.getNvrid());
      naturvardsregistretObject.setWikiDataItem((ItemDocument) getWikiData().getDataFetcher().getEntityDocument(naturvardsregistretObject.getWikiDataObjectKey()));
    }

/*
    ███████╗██╗   ██╗ █████╗ ██╗     ██╗   ██╗ █████╗ ████████╗███████╗    ██████╗ ███████╗██╗  ████████╗ █████╗
    ██╔════╝██║   ██║██╔══██╗██║     ██║   ██║██╔══██╗╚══██╔══╝██╔════╝    ██╔══██╗██╔════╝██║  ╚══██╔══╝██╔══██╗
    █████╗  ██║   ██║███████║██║     ██║   ██║███████║   ██║   █████╗      ██║  ██║█████╗  ██║     ██║   ███████║
    ██╔══╝  ╚██╗ ██╔╝██╔══██║██║     ██║   ██║██╔══██║   ██║   ██╔══╝      ██║  ██║██╔══╝  ██║     ██║   ██╔══██║
    ███████╗ ╚████╔╝ ██║  ██║███████╗╚██████╔╝██║  ██║   ██║   ███████╗    ██████╔╝███████╗███████╗██║   ██║  ██║
    ╚══════╝  ╚═══╝  ╚═╝  ╚═╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚══════╝    ╚═════╝ ╚══════╝╚══════╝╚═╝   ╚═╝  ╚═╝
 */
    log.debug("Searching for delta between local data and WikiData item");

    List<Statement> addStatements = new ArrayList();
    List<Statement> deleteStatements = new ArrayList();

    // todo assert instance of nature reserve and nvrid. if not then fail!

    evaluateDelta(naturvardsregistretObject, addStatements, deleteStatements);


/*
     ██████╗ ██████╗ ███╗   ███╗███╗   ███╗██╗████████╗    ██╗    ██╗██╗██╗  ██╗██╗██████╗  █████╗ ████████╗ █████╗
    ██╔════╝██╔═══██╗████╗ ████║████╗ ████║██║╚══██╔══╝    ██║    ██║██║██║ ██╔╝██║██╔══██╗██╔══██╗╚══██╔══╝██╔══██╗
    ██║     ██║   ██║██╔████╔██║██╔████╔██║██║   ██║       ██║ █╗ ██║██║█████╔╝ ██║██║  ██║███████║   ██║   ███████║
    ██║     ██║   ██║██║╚██╔╝██║██║╚██╔╝██║██║   ██║       ██║███╗██║██║██╔═██╗ ██║██║  ██║██╔══██║   ██║   ██╔══██║
    ╚██████╗╚██████╔╝██║ ╚═╝ ██║██║ ╚═╝ ██║██║   ██║       ╚███╔███╔╝██║██║  ██╗██║██████╔╝██║  ██║   ██║   ██║  ██║
     ╚═════╝ ╚═════╝ ╚═╝     ╚═╝╚═╝     ╚═╝╚═╝   ╚═╝        ╚══╝╚══╝ ╚═╝╚═╝  ╚═╝╚═╝╚═════╝ ╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝
 */

    if (!addStatements.isEmpty() || !deleteStatements.isEmpty()) {

      log.debug("Statements has been updated.");

      if (!addStatements.isEmpty()) {
        log.debug("Adding {} statements.", addStatements.size());
        for (Statement statement : addStatements) {
          log.trace("Adding statement:\n{}", statement);
        }
      }
      if (!deleteStatements.isEmpty()) {
        log.debug("Deleting {} statements.", deleteStatements.size());
        for (Statement statement : deleteStatements) {
          log.trace("{}", statement);
        }
      }

      if (!isDryRun()) {
        getWikiData().getDataEditor().updateStatements(naturvardsregistretObject.getWikiDataItem().getEntityId(),
            addStatements,
            deleteStatements,
            "Bot updated due to delta found compared to local data from Naturvårdsverket", Collections.emptyList());

        log.info("Committed statements diff to WikiData.");
      }
    } else {
      log.debug("No statements has been updated.");
    }
    log.trace("Done processing nvrid {}", naturvardsregistretObject.getNvrid());
  }

  protected void evaluateDelta(
      NaturvardsregistretObject naturvardsregistretObject,
      List<Statement> addStatements, List<Statement> deleteStatements
  ) throws Exception {

    // inception date
    Statement existingInceptionDate = wikiData.findMostRecentPublishedStatement(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("inception date"));
    if (existingInceptionDate == null
        || (!existingInceptionDate.getValue().equals(inceptionDateValueFactory(naturvardsregistretObject)))) {
      addStatements.add(inceptionDateStatementFactory(naturvardsregistretObject));
    }

    // iunc cateogory
    // todo lookup wikidata if "Not Applicable" is in use as a category!
    // todo see https://www.protectedplanet.net/c/wdpa-lookup-tables
    // not applicable seems to be set a null value link?
    // johannisberg is as null: https://www.wikidata.org/wiki/Q30180845
    String iuncCategoryValue = naturvardsregistretObject.getFeature().getProperty("IUCNKAT");
    if (iuncCategoryValue != null) {
      iuncCategoryValue = iuncCategoryValue.replaceFirst("^\\s*([^,]+).*", "$1").trim().toUpperCase();
      EntityIdValue iuncCategory = iuncCategories.get(iuncCategoryValue);
      if (iuncCategory == null) {
        log.warn("Unsupported IUNC category in feature: {}", naturvardsregistretObject.getFeature().getProperties());
      } else {
        Statement existingIuncCategory = wikiData.findMostRecentPublishedStatement(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("IUCN protected areas category"));
        if (existingIuncCategory == null
            || ((iuncCategory != WikiData.NULL_ENTITY_VALUE || existingIuncCategory.getValue() != null)
            && !iuncCategory.equals(existingIuncCategory.getValue()))) {
          addStatements.add(iuncCategoryStatementFactory(iuncCategory, naturvardsregistretObject));
        }
      }
    }

    // country
    Statement existingCountry = wikiData.findMostRecentPublishedStatement(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("country"));
    if (existingCountry == null
        || (!existingCountry.getValue().equals(getWikiData().entity("Sweden")))) {
      addStatements.add(countryStatementFactory(naturvardsregistretObject));
    }

    // operator
    naturvardsregistretObject.setOperatorWikiDataItem(operatorsByNvrProperty.get((String) naturvardsregistretObject.getFeature().getProperty("FORVALTARE")));
    if (naturvardsregistretObject.getOperatorWikiDataItem() == null) {
      log.warn("Unable to lookup operator Q for '{}' Operator claims will not be touched.", (String) naturvardsregistretObject.getFeature().getProperty("FORVALTARE"));
    } else {
      Statement existingOperator = wikiData.findMostRecentPublishedStatement(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("operator"));
      if (existingOperator == null
          || !existingOperator.getValue().equals(naturvardsregistretObject.getOperatorWikiDataItem())) {
        addStatements.add(operatorStatementFactory(naturvardsregistretObject));
      }
    }


    // municipality, can be multiple separated by comma
    // todo


    // area

    Statement existingArea = wikiData.findStatementWithoutQualifier(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("area"));
    if (existingArea == null
        || !existingArea.getValue().equals(areaValueFactory(naturvardsregistretObject))) {
      addStatements.add(areaStatementFactory(naturvardsregistretObject));
    }

    Statement existingLandArea = wikiData.findStatementByUniqueQualifier(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("area"), getWikiData().property("applies to part"), getWikiData().entity("land"));
    if (existingLandArea == null
        || !existingLandArea.getValue().equals(areaLandValueFactory(naturvardsregistretObject))) {
      addStatements.add(areaLandStatementFactory(naturvardsregistretObject));
    }

    Statement existingForestArea = wikiData.findStatementByUniqueQualifier(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("area"), getWikiData().property("applies to part"), getWikiData().entity("forest"));
    if (existingForestArea == null
        || !existingForestArea.getValue().equals(areaForestValueFactory(naturvardsregistretObject))) {
      addStatements.add(areaForestStatementFactory(naturvardsregistretObject));
    }


    Statement existingBodyOfWaterArea = wikiData.findStatementByUniqueQualifier(naturvardsregistretObject.getWikiDataItem(), getWikiData().property("area"), getWikiData().property("applies to part"), getWikiData().entity("body of water"));
    if (existingBodyOfWaterArea == null
        || !existingBodyOfWaterArea.getValue().equals(areaBodyOfWaterValueFactory(naturvardsregistretObject))) {
      addStatements.add(areaBodyOfWaterStatementFactory(naturvardsregistretObject));
    }

    System.currentTimeMillis();

    // todo requires REST API access. see https://api.protectedplanet.net/documentation
    // todo i have requested key to karl.wettin@wikimedia.se
    // wdpaid
//    Statement existingWdpaId = natureReserve.wikiDataItem.findStatement(property("wdpaid"));
//    if (existingWdpaId != null) {
//      if (!existingWdpaId.getValue().equals(wdpaidValueFactory(natureReserve))) {
//        deleteStatements.add(existingWdpaId);
//        addStatements.add(wdpaidStatementFactory(natureReserve));
//      }
//    } else {
//      addStatements.add(wdpaidStatementFactory(natureReserve));
//    }


    if (!naturvardsregistretObject.getFeature().getGeometry().accept(
        new GeometryStrategy(
            this,
            naturvardsregistretObject,
            addStatements, deleteStatements
        ))) {
      log.error("Unable to process geometry {}", naturvardsregistretObject.getFeature().getGeometry());
    }
  }


  protected StatementBuilder addNaturvardsregistretReferences(
      NaturvardsregistretObject naturvardsregistretObject,
      StatementBuilder statementBuilder
  ) {
    statementBuilder.withReference(naturvardsregistretReferenceFactory(naturvardsregistretObject));
    statementBuilder.withReference(retrievedReferenceFactory(naturvardsregistretObject));
    statementBuilder.withReference(publishedReferenceFactory(naturvardsregistretObject));
    statementBuilder.withReference(statedInReferenceFactory(naturvardsregistretObject));
    return statementBuilder;
  }

  private Statement iuncCategoryStatementFactory(EntityIdValue iuncCategory, NaturvardsregistretObject naturvardsregistretObject) {
    StatementBuilder statementBuilder = StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("IUCN protected areas category"));
    if (WikiData.NULL_ENTITY_VALUE != iuncCategory) {
      statementBuilder.withValue(iuncCategory);
    }
    return addNaturvardsregistretReferences(naturvardsregistretObject, statementBuilder).build();
  }

  private Statement inceptionDateStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("inception date"))
        .withValue(inceptionDateValueFactory(naturvardsregistretObject))
    ).build();
  }

  private TimeValue inceptionDateValueFactory(NaturvardsregistretObject naturvardsregistretObject) {
    // todo I think inception date should be either IKRAFTDAT or if null URSGALLDAT or if null URBESLDAT, but historically we used URSBESLDAT

    String inceptionDateString = naturvardsregistretObject.getFeature().getProperty("IKRAFTDAT");
    if (inceptionDateString == null) {
      inceptionDateString = naturvardsregistretObject.getFeature().getProperty("URSGALLDAT");
    }
    if (inceptionDateString == null) {
      inceptionDateString = naturvardsregistretObject.getFeature().getProperty("URSBESLDAT");
    }
    if (inceptionDateString == null) {
      throw new RuntimeException("No candidates for inception date found!");
    }
    LocalDate inceptionDate = LocalDate.parse(inceptionDateString, featureValueDateFormatter);
    return wikiData.toTimeValue(inceptionDate);
  }

  private Statement countryStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("country"))
        .withValue(getWikiData().entity("Sweden"))
    ).build();
  }

  private Statement operatorStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("operator"))
        .withValue(naturvardsregistretObject.getOperatorWikiDataItem())
    ).build();
  }

  private QuantityValue areaValueFactory(NaturvardsregistretObject naturvardsregistretObject, String property) {
    return new QuantityValueImpl(
        BigDecimal.valueOf(((Number) naturvardsregistretObject.getFeature().getProperty(property)).doubleValue()),
        null, null,
        getWikiData().entity("hectare").getIri()
    );
  }

  private Statement areaStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("area"))
        .withValue(areaValueFactory(naturvardsregistretObject))
    ).build();
  }

  private QuantityValue areaValueFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return areaValueFactory(naturvardsregistretObject, "AREA_HA");
  }

  private Statement areaForestStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("area"))
        .withValue(areaForestValueFactory(naturvardsregistretObject))
    ).build();
  }

  private QuantityValue areaForestValueFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return areaValueFactory(naturvardsregistretObject, "SKOG_HA");
  }

  private Statement areaLandStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("area"))
        .withValue(areaLandValueFactory(naturvardsregistretObject))
    ).build();
  }

  private QuantityValue areaLandValueFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return areaValueFactory(naturvardsregistretObject, "LAND_HA");
  }

  private Statement areaBodyOfWaterStatementFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return addNaturvardsregistretReferences(naturvardsregistretObject, StatementBuilder
        .forSubjectAndProperty(ItemIdValue.NULL, getWikiData().property("area"))
        .withValue(areaBodyOfWaterValueFactory(naturvardsregistretObject))
    ).build();
  }

  private QuantityValue areaBodyOfWaterValueFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return areaValueFactory(naturvardsregistretObject, "VATTEN_HA");
  }

  private Reference naturvardsregistretReferenceFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return ReferenceBuilder.newInstance()
        .withPropertyValue(getWikiData().property("reference URL"), new StringValueImpl(
            "http://nvpub.vic-metria.nu/naturvardsregistret/rest/omrade/" + naturvardsregistretObject.getNvrid() + "/G%C3%A4llande"))
        .build();
  }

  private Reference retrievedReferenceFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return ReferenceBuilder.newInstance()
        .withPropertyValue(getWikiData().property("retrieved"), wikiData.toTimeValue(naturvardsregistretObject.getRetrievedDate()))
        .build();
  }

  private Reference publishedReferenceFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return ReferenceBuilder.newInstance()
        .withPropertyValue(getWikiData().property("publication date"), wikiData.toTimeValue(naturvardsregistretObject.getPublishedDate()))
        .build();
  }

  private Reference statedInReferenceFactory(NaturvardsregistretObject naturvardsregistretObject) {
    return ReferenceBuilder.newInstance()
        .withPropertyValue(getWikiData().property("stated in"), getWikiData().entity("Protected Areas (Nature Reserves)"))
        .build();
  }


}