package author.testutil;

import org.jsoup.nodes.Element;
import org.assertj.core.api.AbstractAssert;

public class ElementAssert extends AbstractAssert<ElementAssert, Element> {

  public ElementAssert(Element actual) {
    super(actual, ElementAssert.class);
  }

  public static ElementAssert assertThat(Element actual) {
    return new ElementAssert(actual);
  }

  public ElementAssert is(String selector) {
    isNotNull();
    if (!actual.is(selector))
      failWithMessage("Expected element to be a %s but was %s", selector, actual);
    return this;
  }

  public ElementAssert hasAttr(String key, String value) {
    isNotNull();
    if (!actual.attributes().hasKey(key))
      failWithMessage("Expected element to have attribute %s but it didn't", key);
    if (!actual.attributes().get(key).equals(value))
      failWithMessage("Expected element attribute '%s' to be '%s' but it was '%s'",
        key, value, actual.attributes().get(key));
    return this;
  }

  public ElementAssert hasText(String text) {
    isNotNull();
    if (!actual.text().equals(text))
      failWithMessage("Expected element to have text '%s' but is was '%s'", text, actual.text());
    return this;
  }

  public ElementAssert hasHtml(String html) {
    isNotNull();
    if (!actual.html().equals(html))
      failWithMessage("Expected element to have html '%s' but it was '%s'", html, actual.html());
    return this;
  }

  public ElementAssert hasId(String id) {
    isNotNull();
    if (!actual.id().equals(id))
      failWithMessage("Expected element to have id '%s' but it was '%s'", id, actual.id());
    return this;
  }

  public ElementAssert hasVal(String val) {
    isNotNull();
    if (!actual.val().equals(val))
      failWithMessage("Expected element to have value '%s' but it was '%s'", val, actual.val());
    return this;
  }

}
