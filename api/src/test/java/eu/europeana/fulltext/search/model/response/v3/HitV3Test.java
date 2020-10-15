package eu.europeana.fulltext.search.model.response.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europeana.fulltext.api.config.SerializationConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = SerializationConfig.class)
public class HitV3Test {

    @Autowired
    private ObjectMapper objectMapper;

    private JacksonTester<HitV3> json;

    @Before
    public void setup() {
        JacksonTester.initFields(this, objectMapper);
    }

//    @Test
//    public void testSerialization() throws IOException {
//        JsonContent<HitV3> serialized = json.write(new HitV3(10, 100, "exact"));
//        assertThat(serialized).hasJsonPathArrayValue("@.annotations");
//        assertThat(serialized).hasJsonPathArrayValue("@.selectors");
//        assertThat(serialized).extractingJsonPathStringValue("@.type")
//                .isEqualTo("Hit");
//    }
}