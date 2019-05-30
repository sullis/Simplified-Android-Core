package org.nypl.simplified.opds.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.mime.MIMEParser;
import org.nypl.simplified.mime.MIMEType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.nypl.simplified.opds.core.OPDSFeedConstants.ACQUISITION_URI_PREFIX_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.ALTERNATE_REL_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.ANNOTATION_URI_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.ATOM_URI;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.BIBFRAME_URI;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.DUBLIN_CORE_TERMS_URI;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.GROUP_REL_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.IMAGE_URI_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.ISSUES_REL_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.OPDS_URI;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.RELATED_REL_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.REVOKE_URI_TEXT;
import static org.nypl.simplified.opds.core.OPDSFeedConstants.THUMBNAIL_URI_TEXT;

/**
 * The default implementation of the {@link OPDSAcquisitionFeedEntryParserType}
 * type.
 */

public final class OPDSAcquisitionFeedEntryParser implements OPDSAcquisitionFeedEntryParserType {

  private OPDSAcquisitionFeedEntryParser() {

  }

  private void findAcquisitionAuthors(
    final Element element,
    final OPDSAcquisitionFeedEntryBuilderType eb)
    throws OPDSParseException {

    final List<Element> e_authors =
      OPDSXML.getChildElementsWithName(element, ATOM_URI, "author");
    for (final Element ea : e_authors) {
      final String name =
        OPDSXML.getFirstChildElementTextWithName(NullCheck.notNull(ea), ATOM_URI, "name");
      eb.addAuthor(name);
    }
  }

  private OptionType<String> findPublisher(final Element element) {
    return OPDSXML.getFirstChildElementTextWithNameOptional(
      element, DUBLIN_CORE_TERMS_URI, "publisher");
  }

  private String findDistribution(final Element element) {
    return OPDSXML.getFirstChildElementTextWithName(
      element, BIBFRAME_URI, "distribution", "ProviderName");
  }

  /**
   * @return A new feed entry parser
   */

  public static OPDSAcquisitionFeedEntryParserType newParser() {
    return new OPDSAcquisitionFeedEntryParser();
  }

  private OPDSAcquisitionFeedEntry parseAcquisitionEntry(
    final Element element)
      throws Exception {

    final String id = OPDSAtom.findID(element);
    final String title = OPDSAtom.findTitle(element);
    final Calendar updated = OPDSAtom.findUpdated(element);

    final OPDSAcquisitionFeedEntryBuilderType entry_builder =
      OPDSAcquisitionFeedEntry.newBuilder(id, title, updated, OPDSAvailabilityLoanable.get());

    final List<Element> e_links =
      OPDSXML.getChildElementsWithNameNonEmpty(element, ATOM_URI, "link");

    /*
     * First, locate a revocation link, if any. This is required to be found
     * first as it needs to be used later in availability information.
     */

    final OptionType<URI> revoke = findRevocationLink(e_links);

    /*
     * Now, handle any other types of links.
     */

    for (final Element e_link : e_links) {
      if (e_link.hasAttribute("rel")) {
        final String rel_text = NullCheck.notNull(e_link.getAttribute("rel"));

        if (tryConsumeLinkGroup(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkIssues(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkAlternate(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkAnalytics(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkRelated(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkAnnotation(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkThumbnail(entry_builder, e_link, rel_text)) {
          continue;
        }

        if (tryConsumeLinkCover(entry_builder, e_link, rel_text)) {
          continue;
        }

        tryConsumeAcquisitions(entry_builder, revoke, e_link, rel_text);
      }
    }

    parseCategories(element, entry_builder);

    findAcquisitionAuthors(element, entry_builder);
    entry_builder.setPublisherOption(findPublisher(element));
    entry_builder.setDistribution(findDistribution(element));
    entry_builder.setPublishedOption(OPDSAtom.findPublished(element));

    entry_builder.setSummaryOption(
      OPDSXML.getFirstChildElementTextWithNameOptional(element, ATOM_URI, "summary"));

    return entry_builder.build();
  }

  private void tryConsumeDRMLicensorInformation(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link)
    throws OPDSParseException {

    final OptionType<Element> licensor_opt =
      OPDSXML.getFirstChildElementWithNameOptional(
        e_link, OPDSFeedConstants.DRM_URI, "licensor");

    if (licensor_opt.isSome()) {
      final Some<Element> licensor_some = (Some<Element>) licensor_opt;

      final Element licensor_element = OPDSXML.nodeAsElement(licensor_some.get());
      final String vendor = licensor_element.getAttribute("drm:vendor");
      String client_token = null;
      OptionType<String> device_manager = Option.none();

      final NodeList licensor_children = licensor_element.getChildNodes();
      for (int i = 0; i < licensor_children.getLength(); ++i) {
        final Node node = licensor_children.item(i);

        if (node.getNodeName().contains("clientToken")) {
          client_token = node.getFirstChild().getNodeValue();
        }

        if (node.getNodeName().contains("link")) {
          final Element element = OPDSXML.nodeAsElement(node);
          final boolean has_everything =
            element.hasAttribute("rel") && element.hasAttribute("href");

          if (has_everything) {
            final String r = NullCheck.notNull(element.getAttribute("rel"));
            final String h = NullCheck.notNull(element.getAttribute("href"));

            if ("http://librarysimplified.org/terms/drm/rel/devices".equals(r)) {
              device_manager = Option.some(h);
            }
          }
        }
        if (vendor != null && client_token != null) {
          final DRMLicensor licensor = new DRMLicensor(vendor, client_token, device_manager);
          entry_builder.setLicensorOption(Option.some(licensor));
        }
      }
    }
  }

  private OPDSIndirectAcquisition parseIndirectAcquisition(
    final Element acquisition)
      throws Exception {
    final MIMEType type =
      MIMEParser.Companion.parseRaisingException(acquisition.getAttribute("type"));
    final List<OPDSIndirectAcquisition> next_acquisitions = parseIndirectAcquisitions(acquisition);
    return new OPDSIndirectAcquisition(type, next_acquisitions);
  }

  private List<OPDSIndirectAcquisition> parseIndirectAcquisitions(
    final Element element)
      throws Exception {
    final List<Element> indirect_elements =
      OPDSXML.getChildElementsWithName(element, OPDS_URI, "indirectAcquisition");
    final List<OPDSIndirectAcquisition> indirects =
      new ArrayList<>(indirect_elements.size());

    for (final Element indirect_element : indirect_elements) {
      indirects.add(parseIndirectAcquisition(indirect_element));
    }
    return indirects;
  }

  private void tryConsumeAcquisitions(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final OptionType<URI> revoke,
    final Element link,
    final String rel_text)
      throws Exception {

    if (rel_text.startsWith(ACQUISITION_URI_PREFIX_TEXT)) {
      for (final OPDSAcquisitionRelation v : OPDSAcquisitionRelation.values()) {
        final String uri_text = v.getUri().toString();
        if (rel_text.equals(uri_text)) {
          final URI href = new URI(link.getAttribute("href"));

          final List<OPDSIndirectAcquisition> indirects = parseIndirectAcquisitions(link);
          final OptionType<MIMEType> type = typeAttribute(link);

          if (type.isSome() || !indirects.isEmpty()) {
            final OPDSAcquisition acquisition = new OPDSAcquisition(v, href, type, indirects);
            entry_builder.addAcquisition(acquisition);

            if (v == OPDSAcquisitionRelation.ACQUISITION_OPEN_ACCESS) {
              entry_builder.setAvailability(OPDSAvailabilityOpenAccess.get(revoke));
            } else {
              tryAvailability(entry_builder, link, revoke);
            }
          }
        }
      }

      tryConsumeDRMLicensorInformation(entry_builder, link);
    }
  }

  private void parseCategories(
    final Element element,
    final OPDSAcquisitionFeedEntryBuilderType entry_builder) {

    final List<Element> e_categories =
      OPDSXML.getChildElementsWithName(element, ATOM_URI, "category");

    for (final Element ce : e_categories) {
      final String term = NullCheck.notNull(ce.getAttribute("term"));
      final String scheme = NullCheck.notNull(ce.getAttribute("scheme"));

      final OptionType<String> label;
      if (ce.hasAttribute("label")) {
        label = Option.some(ce.getAttribute("label"));
      } else {
        label = Option.none();
      }

      entry_builder.addCategory(new OPDSCategory(term, scheme, label));
    }
  }

  /**
   * Check if the given link refers to a cover image. If it is, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkCover(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(IMAGE_URI_TEXT)) {
      if (e_link.hasAttribute("href")) {
        final URI u = new URI(e_link.getAttribute("href"));
        entry_builder.setCoverOption(Option.some(u));
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the given link refers to a thumbnail. If it is, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkThumbnail(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(THUMBNAIL_URI_TEXT)) {
      if (e_link.hasAttribute("href")) {
        final URI u = new URI(e_link.getAttribute("href"));
        entry_builder.setThumbnailOption(Option.some(u));
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the given link refers to an annotation. If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkAnnotation(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(ANNOTATION_URI_TEXT)) {
      if (e_link.hasAttribute("href")) {
        final URI u = new URI(e_link.getAttribute("href"));
        entry_builder.setAnnotationsOption(Option.some(u));
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the given link refers to related books. If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkRelated(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(RELATED_REL_TEXT)) {
      if (e_link.hasAttribute("href")) {
        final URI u = new URI(e_link.getAttribute("href"));
        entry_builder.setRelatedOption(Option.some(u));
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the given link refers to an "alternate". If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkAlternate(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(ALTERNATE_REL_TEXT)) {
      final String uri_text = NullCheck.notNull(e_link.getAttribute("href"));
      final URI uri = new URI(uri_text);
      entry_builder.setAlternateOption(Option.some(uri));
      return true;
    }
    return false;
  }

  /**
   * Check if the given link refers to analytics. If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkAnalytics(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT)) {
      final String uri_text = NullCheck.notNull(e_link.getAttribute("href"));
      final URI uri = new URI(uri_text);
      entry_builder.setAnalyticsOption(Option.some(uri));
      return true;
    }
    return false;
  }

  /**
   * Check if the given link refers to an issue system. If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkIssues(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(ISSUES_REL_TEXT)) {
      final String uri_text = NullCheck.notNull(e_link.getAttribute("href"));
      final URI uri = new URI(uri_text);
      entry_builder.setIssuesOption(Option.some(uri));
      return true;
    }
    return false;
  }

  /**
   * Check if the given link refers to a group. If it does, add it to the builder and
   * return {@code true}.
   */

  private boolean tryConsumeLinkGroup(
    final OPDSAcquisitionFeedEntryBuilderType entry_builder,
    final Element e_link,
    final String rel_text)
    throws URISyntaxException {

    if (rel_text.equals(GROUP_REL_TEXT)) {
      final String uri_text = NullCheck.notNull(e_link.getAttribute("href"));
      final String link_title = NullCheck.notNull(e_link.getAttribute("title"));
      final URI uri = new URI(uri_text);
      entry_builder.addGroup(uri, link_title);
      return true;
    }
    return false;
  }

  private OptionType<URI> findRevocationLink(
    final List<Element> e_links)
    throws URISyntaxException {

    for (final Element e_link : e_links) {
      if (e_link.hasAttribute("rel")) {
        final String rel_text = NullCheck.notNull(e_link.getAttribute("rel"));
        if (rel_text.equals(REVOKE_URI_TEXT)) {
          if (e_link.hasAttribute("href")) {
            final URI u = new URI(e_link.getAttribute("href"));
            return Option.some(u);
          }
        }
      }
    }
    return Option.none();
  }

  private OptionType<MIMEType> typeAttribute(
    final Element acquisition) throws Exception {
    final String type = acquisition.getAttribute("type");
    if (type != null && !type.isEmpty()) {
      return Option.some(MIMEParser.Companion.parseRaisingException(type));
    }
    return Option.none();
  }

  private void tryAvailability(
    final OPDSAcquisitionFeedEntryBuilderType eb,
    final Element element,
    final OptionType<URI> revoke)
    throws OPDSParseException {

    final OptionType<Element> copies_opt =
      OPDSXML.getFirstChildElementWithNameOptional(element, OPDS_URI, "copies");
    final OptionType<Element> holds_opt =
      OPDSXML.getFirstChildElementWithNameOptional(element, OPDS_URI, "holds");

    eb.setAvailability(inferAvailability(element, copies_opt, holds_opt, revoke));
  }

  private OPDSAvailabilityType inferAvailability(
    final Element element,
    final OptionType<Element> copies_opt,
    final OptionType<Element> holds_opt,
    final OptionType<URI> revoke)
    throws OPDSParseException {

    final OptionType<Element> available_opt =
      OPDSXML.getFirstChildElementWithNameOptional(element, OPDS_URI, "availability");

    if (available_opt.isSome()) {
      final Some<Element> available_some = (Some<Element>) available_opt;
      final Element available = available_some.get();
      final String status = available.getAttribute("status");

      if ("ready".equals(status)) {
        final OptionType<Calendar> end_date =
          OPDSXML.getAttributeRFC3339Optional(available, "until");
        return OPDSAvailabilityHeldReady.get(end_date, revoke);
      }

      if ("reserved".equals(status)) {
        final OptionType<Calendar> end_date =
          OPDSXML.getAttributeRFC3339Optional(available, "until");
        final OptionType<Calendar> start_date =
          OPDSXML.getAttributeRFC3339Optional(available, "since");
        OptionType<Integer> queue = Option.none();
        if (holds_opt.isSome()) {
          final Some<Element> holds_some = (Some<Element>) holds_opt;
          queue = OPDSXML.getAttributeIntegerOptional(holds_some.get(), "position");
        }
        return OPDSAvailabilityHeld.get(start_date, queue, end_date, revoke);
      }

      if ("available".equals(status)) {
        final OptionType<Calendar> end_date =
          OPDSXML.getAttributeRFC3339Optional(available, "until");
        final OptionType<Calendar> start_date =
          OPDSXML.getAttributeRFC3339Optional(available, "since");
        final String rel = NullCheck.notNull(element.getAttribute("rel"));
        if (OPDSAcquisitionRelation.ACQUISITION_BORROW.getUri().toString().equals(rel)) {
          return OPDSAvailabilityLoanable.get();
        } else if (OPDSAcquisitionRelation.ACQUISITION_GENERIC.getUri().toString().equals(rel)) {
          return OPDSAvailabilityLoaned.get(start_date, end_date, revoke);
        }
      }
    }

    /*
     * The user has never seen the book before, and the book
     * did not have an availability:available element for its
     * borrow link, so it must be holdable.
     */

    if (copies_opt.isSome()) {
      final Some<Element> copies_some = (Some<Element>) copies_opt;
      final int copies_available =
        OPDSXML.getAttributeInteger(copies_some.get(), "available");

      if (copies_available > 0) {
        return OPDSAvailabilityLoanable.get();
      }
    }

    return OPDSAvailabilityHoldable.get();
  }

  @Override
  public OPDSAcquisitionFeedEntry parseEntry(
    final Element element)
    throws OPDSParseException {

    NullCheck.notNull(element, "Element");
    try {
      return parseAcquisitionEntry(element);
    } catch (final Exception ex) {
      throw new OPDSParseException(ex);
    }
  }

  @Override
  public OPDSAcquisitionFeedEntry parseEntryStream(
    final InputStream stream)
    throws OPDSParseException {

    NullCheck.notNull(stream, "Stream");
    try {
      final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      final DocumentBuilder db = dbf.newDocumentBuilder();
      final Document d = NullCheck.notNull(db.parse(stream));
      final Element e = NullCheck.notNull(d.getDocumentElement());
      return this.parseEntry(e);
    } catch (final ParserConfigurationException | IOException | SAXException ex) {
      throw new OPDSParseException(ex);
    }
  }
}
