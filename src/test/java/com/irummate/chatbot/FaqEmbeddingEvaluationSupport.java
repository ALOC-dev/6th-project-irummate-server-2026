package com.irummate.chatbot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/** 테스트 전용 벡터 계산 및 로컬 캐시. 운영 검색에서 사용하지 않는다. */
final class FaqEmbeddingEvaluationSupport {
    static final String MODEL = "text-embedding-3-small";
    static final String LARGE_MODEL = "text-embedding-3-large";
    static final int DIMENSIONS = 1536;

    private FaqEmbeddingEvaluationSupport() {
    }

    static double l2(float[] query, float[] faq) {
        validate(query);
        validate(faq);
        double sum = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            double difference = (double) query[i] - faq[i];
            sum += difference * difference;
        }
        return Math.sqrt(sum);
    }

    static List<Match> top3(float[] query, List<FaqVector> faqs) {
        return faqs.stream().map(faq -> new Match(faq.id(), faq.question(), l2(query, faq.embedding())))
                .sorted(Comparator.comparingDouble(Match::distance).thenComparingLong(Match::id))
                .limit(3).toList();
    }

    static void validate(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) {
            throw new IllegalArgumentException("Expected a 1536-dimensional embedding");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
        }
    }

    record FaqVector(long id, String question, float[] embedding) {
    }

    record Match(long id, String question, double distance) {
    }

    static final class EmbeddingCache {
        private static final String FORMAT = "faq-embedding-cache-v1";
        private final Path directory;
        private final String model;
        private int hits;
        private int apiCalls;
        private int invalidEntries;

        EmbeddingCache(Path directory, String model) {
            this.directory = directory;
            this.model = model;
        }

        float[] get(String question, Function<String, float[]> embed) throws IOException {
            Files.createDirectories(directory);
            Path file = directory.resolve(key(question) + ".bin");
            if (Files.exists(file)) {
                float[] cached = read(file, question);
                if (cached != null) {
                    hits++;
                    return cached;
                }
                invalidEntries++;
            }

            apiCalls++;
            float[] vector = embed.apply(question);
            validate(vector);
            write(file, question, vector);
            return vector;
        }

        // 현재 small baseline은 DB가 기준이다. API 호출 없이 같은 입력 텍스트로 캐시를 준비한다.
        boolean seedFromDatabase(String text, float[] vector) throws IOException {
            validate(vector);
            Files.createDirectories(directory);
            Path file = directory.resolve(key(text) + ".bin");
            if (Files.exists(file) && java.util.Arrays.equals(read(file, text), vector)) {
                return false;
            }
            write(file, text, vector);
            return true;
        }

        private void write(Path file, String question, float[] vector) throws IOException {
            // 실행이 중단되어도 이미 생성한 벡터를 재사용하도록 질문마다 저장한다.
            Path temporary = Files.createTempFile(directory, "embedding-", ".tmp");
            try {
                try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                    output.writeUTF(FORMAT);
                    output.writeUTF(model);
                    output.writeUTF(question);
                    output.writeInt(DIMENSIONS);
                    for (float value : vector) {
                        output.writeFloat(value);
                    }
                }
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private float[] read(Path file, String question) throws IOException {
            try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                if (!FORMAT.equals(input.readUTF()) || !model.equals(input.readUTF())
                        || !question.equals(input.readUTF()) || input.readInt() != DIMENSIONS) {
                    return null;
                }
                float[] vector = new float[DIMENSIONS];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = input.readFloat();
                }
                validate(vector);
                return input.read() == -1 ? vector : null;
            } catch (java.io.EOFException | java.io.UTFDataFormatException | IllegalArgumentException exception) {
                return null;
            }
        }

        private String key(String question) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest((model + "\0" + question).getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        String statistics(String label) {
            return label + ": cache hit=" + hits + ", API calls=" + apiCalls + ", invalid entries=" + invalidEntries;
        }

        int hits() {
            return hits;
        }

        int apiCalls() {
            return apiCalls;
        }
    }
}
