package author.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.util.concurrent.*;

import java.io.*;

import java.net.ServerSocket;
import java.net.URI;

import javafx.application.Platform;

import org.assertj.core.api.*;
import static org.assertj.core.api.Assertions.*;

import org.jsoup.*;
import org.jsoup.nodes.*;

import author.events.*;
import author.requests.*;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;
import static author.testutil.ElementAssert.*;


import lombok.val;

class PreviewServerTest {
  
  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();

  int port;
  String previewUrl;
  String checkUrl;
  PreviewServer server;

  @BeforeEach
  void setup() throws Exception {
    try (val socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    } catch (Exception ex) {
      port = 10123;
    }
    previewUrl = "http://localhost:" + port + "/preview";
    checkUrl = "http://localhost:" + port + "/check";
    server = new PreviewServer(apiBus);
    server.start(port);
  }

  @AfterEach
  void teardown() {
    server.stop();
  }

  private static boolean platformStarted;

  @BeforeAll
  static void before() {
    try {
      Platform.startup(() -> {});
      platformStarted = true;
    } catch (IllegalStateException ignore) {}
  }
  @AfterAll
  static void after() {
    if (platformStarted)
      Platform.exit();
  }

  @Test @DisplayName("produces correct quiz preview")
  void getPreview() throws Exception {
    apiBus.emulIn(new ApiResponse.FullQuiz(ViewsTest.quiz("Q1", "", "", List.of())));
    val doc = Jsoup.connect(previewUrl).data("qid", "Q1").get();
    assertThat(doc.selectFirst("h1")).hasText("Q1");
    assertThatExceptionOfType(HttpStatusException.class)
      .isThrownBy(() -> Jsoup.connect(previewUrl).data("qid", "xyz").get())
      .withMessageContaining("Status=410");
    assertThatExceptionOfType(HttpStatusException.class)
      .isThrownBy(() -> Jsoup.connect(previewUrl).get())
      .withMessageContaining("Status=400");
  }
  
  @Test @DisplayName("checks solution and produces correct results")
  void checkItem() throws Exception {
    apiBus.emulIn(new ApiResponse.FullQuiz(ViewsTest.quiz("Q2", "", "", List.of(
      new OutSection("s1", "", "", List.of(
        ViewsTest.singleChoice, ViewsTest.multiChoice, ViewsTest.fillSelect, ViewsTest.fillEnter
      ))
    ))));
    val doc = Jsoup.connect("http://localhost:" + port + "/check")
      .data("qid", "Q2")
      .data("ssc", "s1")
      .data("isc", "sinchoice")
      .data("sol", "1")
      .post();
    assertThat(doc.select("tr")).size().isEqualTo(1);
    val tr = doc.selectFirst("tr");
    assertThat(tr.child(0).child(0)).is("div").hasAttr("class", "neutral").hasText("sinchoice hint 2");
    assertThat(tr.child(1).child(0)).is("div").hasAttr("class", "correct").hasText("sinchoice hint 2");

    val doc2 = Jsoup.connect("http://localhost:" + port + "/check")
      .data("qid", "Q2")
      .data("ssc", "s1")
      .data("isc", "sinchoice")
      .data("sol", "2")
      .post();
    assertThat(doc2.select("tr")).size().isEqualTo(1);
    val tr2 = doc2.selectFirst("tr");
    assertThat(tr2.child(0).child(0)).is("div").hasAttr("class", "neutral").hasText("sinchoice hint 2");
    assertThat(tr2.child(1).child(0)).is("div").hasAttr("class", "incorrect").hasText("sinchoice hint 3");
  }

}
