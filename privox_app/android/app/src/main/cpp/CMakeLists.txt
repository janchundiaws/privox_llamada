cmake_minimum_required(VERSION 3.22.1)

project("oboe_audio_engine")

# Encuentra la librería de Oboe proporcionada por Prefab
find_package(oboe REQUIRED CONFIG)

add_library(
        oboe_audio_engine
        SHARED
        voice_processor.cpp)

find_library(
        log-lib
        log)

# Enlazamos Oboe y Log a nuestra librería
target_link_libraries(
        oboe_audio_engine
        oboe::oboe
        ${log-lib})
