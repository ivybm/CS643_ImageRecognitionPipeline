package com.aws.cloud;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TextRecognition {
    private static final String BUCKET_BASE_URL = "https://cs643-sp25-project1.s3.amazonaws.com/";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/318788690164/ImageRecognition.fifo";

    private static final String OUTPUT_FILE = "/home/ec2-user/output.txt";
    private static final String IMAGE_DIR = "/home/ec2-user/images/";

//    private static final String OUTPUT_FILE = System.getProperty("user.home") + "/output.txt";
//    private static final String IMAGE_DIR = System.getProperty("user.home") + "/images/";

    public static void main(String[] args) {
        // Initialize AWS Clients
        SqsClient sqsClient = SqsClient.create();

        TextractClient textractClient = TextractClient.create();

        // Ensure image directory exists
        new File(IMAGE_DIR).mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE, true))) {
            File outputFile = new File(OUTPUT_FILE);
            outputFile.getParentFile().mkdirs(); // Create parent directory if needed
            outputFile.createNewFile();

            while (true) {
                // Receive messages from SQS
                ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                        .queueUrl(SQS_QUEUE_URL)
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(10)
                        .build();

                List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

                for (Message message : messages) {
                    String imageIndex = message.body(); // e.g., "2.jpg"

                    // Stop processing if message is "-1"
                    if ("-1".equals(imageIndex.trim())) {
                        System.out.println("Received termination signal. Stopping...");
                        deleteMessageFromSQS(sqsClient, message.receiptHandle());
                        writer.close();
                        return;
                    }

                    System.out.println("Processing Image: " + imageIndex);
                    String fileName = downloadImage(imageIndex);
                    if (fileName != null) {
                        String extractedText = extractTextFromImage(textractClient, fileName);
                        if (extractedText != null && !extractedText.isEmpty()) {
                            // Using regex to perform a case-insensitive search
                            Pattern pattern = Pattern.compile("DRIVER LICENSE", Pattern.CASE_INSENSITIVE);
                            Matcher matcher = pattern.matcher(extractedText);

                            if (matcher.find()) {
                                // Record output only if both face and text are found
                                writer.write(imageIndex + ": " + extractedText + "\n");
                                writer.flush();
                            } else {
                                System.err.println("No driver license found in image: " + imageIndex);
                            }
                        }
                    }

                    deleteMessageFromSQS(sqsClient, message.receiptHandle());
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to output file: " + e.getMessage());
        }
    }

    /**
     * Delete message from SQS after processing
     */
    private static void deleteMessageFromSQS(SqsClient sqsClient, String receiptHandle) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .receiptHandle(receiptHandle)
                .build());
    }

    /**
     * Downloads the image from the S3 bucket using HTTP GET.
     */
    private static String downloadImage(String imageIndex) {
        String imageUrl = BUCKET_BASE_URL + imageIndex;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() != 200) {
                System.err.println("Failed to download image: " + imageIndex + " - HTTP Response Code: " + connection.getResponseCode());
                return null;
            }

            String fileName = IMAGE_DIR + imageIndex;
            Path imagePath = Path.of(fileName);
            Files.copy(connection.getInputStream(), imagePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Downloaded: " + imageIndex);
            return imagePath.toString();
        } catch (Exception e) {
            System.err.println("Failed to download image: " + imageIndex + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Uses AWS Textract to extract text from the given image file.
     */
    private static String extractTextFromImage(TextractClient textractClient, String imagePath) {
        try {
            // Convert file path to bytes for AWS Textract
            Path path = Path.of(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);

            AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                    .document(Document.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build())
                    .featureTypes(FeatureType.FORMS)
                    .build();

            AnalyzeDocumentResponse response = textractClient.analyzeDocument(request);
            StringBuilder extractedText = new StringBuilder();

            response.blocks().forEach(block -> {
                if (block.blockType().toString().equals("LINE")) {
                    extractedText.append(block.text()).append(" ");
                }
            });

            return extractedText.toString().trim();
        } catch (Exception e) {
            System.err.println("Failed to extract text from image: " + imagePath + " - " + e.getMessage());
            return null;
        }
    }

}

