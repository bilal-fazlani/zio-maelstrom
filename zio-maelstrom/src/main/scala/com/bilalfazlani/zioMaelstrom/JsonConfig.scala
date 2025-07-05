package com.bilalfazlani.zioMaelstrom

import zio.json.*
import zio.json.JsonCodecConfiguration.SumTypeHandling.DiscriminatorField

given jsonConfig:JsonCodecConfiguration = JsonCodecConfiguration(
  sumTypeHandling = DiscriminatorField("type"),
  fieldNameMapping = SnakeCase,
  allowExtraFields = true,
  sumTypeMapping = SnakeCase,
  explicitNulls = false,
  explicitEmptyCollections = ExplicitEmptyCollections(true, true),
  enumValuesAsStrings = true
)