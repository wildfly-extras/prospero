import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

import java.nio.file.Files
import java.nio.file.Path

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import static com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS

def manifestFile = properties['manifestFile']
def fieldsToRemove= ["name", "id", "description"]
println("Removing $fieldsToRemove fields from the manifest: $manifestFile")

def YAML_FACTORY = new YAMLFactory()
        .configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
def OBJECT_MAPPER = new ObjectMapper(YAML_FACTORY)
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(ORDER_MAP_ENTRIES_BY_KEYS, true)
def manifestText = Files.readString(Path.of(manifestFile))
def entries = OBJECT_MAPPER.readValue((String)manifestText, Map.class)

fieldsToRemove.forEach {entries.remove(it) }

OBJECT_MAPPER.writeValue(new File(manifestFile), entries)