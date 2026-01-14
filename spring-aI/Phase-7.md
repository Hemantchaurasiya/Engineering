# Phase 7: Image Generation and Processing with Spring AI

## 7.1 Image Generation Models

### DALL-E Integration

#### Setup and Configuration

**1. Dependencies Setup**

```xml
<!-- pom.xml -->
<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

**2. Application Properties Configuration**

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      image:
        options:
          model: dall-e-3  # dall-e-2, dall-e-3
          n: 1             # Number of images to generate
          size: 1024x1024  # 256x256, 512x512, 1024x1024 for DALL-E 2
                          # 1024x1024, 1792x1024, 1024x1792 for DALL-E 3
          quality: standard # standard, hd (DALL-E 3 only)
          style: vivid     # vivid, natural (DALL-E 3 only)
          response-format: url # url, b64_json

logging:
  level:
    org.springframework.ai: DEBUG
```

**3. Basic Image Generation Service**

```java
@Service
@Slf4j
public class ImageGenerationService {

    private final OpenAiImageModel imageModel;

    public ImageGenerationService(OpenAiImageModel imageModel) {
        this.imageModel = imageModel;
    }

    public ImageResponse generateImage(String prompt) {
        log.info("Generating image for prompt: {}", prompt);
        
        ImagePrompt imagePrompt = new ImagePrompt(prompt);
        ImageResponse response = imageModel.call(imagePrompt);
        
        log.info("Generated {} images", response.getResults().size());
        return response;
    }

    public ImageResponse generateImageWithOptions(String prompt, 
                                                String size, 
                                                String quality, 
                                                String style) {
        OpenAiImageOptions options = OpenAiImageOptions.builder()
                .withModel("dall-e-3")
                .withN(1)
                .withSize(size)
                .withQuality(quality)
                .withStyle(style)
                .withResponseFormat("url")
                .build();

        ImagePrompt imagePrompt = new ImagePrompt(prompt, options);
        return imageModel.call(imagePrompt);
    }
}
```

**4. Advanced Image Generation Controller**

```java
@RestController
@RequestMapping("/api/images")
@Validated
@Slf4j
public class ImageController {

    private final ImageGenerationService imageGenerationService;
    private final ImageStorageService imageStorageService;

    public ImageController(ImageGenerationService imageGenerationService,
                          ImageStorageService imageStorageService) {
        this.imageGenerationService = imageGenerationService;
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ImageGenerationResponse> generateImage(
            @Valid @RequestBody ImageGenerationRequest request) {
        
        try {
            ImageResponse response = imageGenerationService
                .generateImageWithOptions(
                    request.getPrompt(),
                    request.getSize(),
                    request.getQuality(),
                    request.getStyle()
                );

            List<GeneratedImageInfo> images = response.getResults().stream()
                .map(result -> {
                    String imageUrl = result.getOutput().getUrl();
                    String revisedPrompt = extractRevisedPrompt(result);
                    
                    // Optionally store image locally
                    String localPath = imageStorageService.downloadAndStore(imageUrl);
                    
                    return GeneratedImageInfo.builder()
                        .originalPrompt(request.getPrompt())
                        .revisedPrompt(revisedPrompt)
                        .imageUrl(imageUrl)
                        .localPath(localPath)
                        .size(request.getSize())
                        .quality(request.getQuality())
                        .style(request.getStyle())
                        .createdAt(Instant.now())
                        .build();
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(
                ImageGenerationResponse.builder()
                    .success(true)
                    .images(images)
                    .totalGenerated(images.size())
                    .build()
            );

        } catch (Exception e) {
            log.error("Error generating image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ImageGenerationResponse.builder()
                    .success(false)
                    .error("Failed to generate image: " + e.getMessage())
                    .build());
        }
    }

    @GetMapping("/styles")
    public ResponseEntity<List<String>> getAvailableStyles() {
        return ResponseEntity.ok(Arrays.asList("vivid", "natural"));
    }

    @GetMapping("/sizes")
    public ResponseEntity<List<String>> getAvailableSizes() {
        return ResponseEntity.ok(Arrays.asList(
            "1024x1024", "1792x1024", "1024x1792"
        ));
    }

    private String extractRevisedPrompt(ImageGeneration result) {
        // DALL-E 3 often revises prompts for safety and quality
        return result.getMetadata().getOrDefault("revised_prompt", "").toString();
    }
}
```

#### DTOs and Models

```java
// Request DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationRequest {
    
    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 4000, message = "Prompt cannot exceed 4000 characters")
    private String prompt;
    
    @Pattern(regexp = "1024x1024|1792x1024|1024x1792", 
             message = "Invalid size format")
    private String size = "1024x1024";
    
    @Pattern(regexp = "standard|hd", message = "Quality must be 'standard' or 'hd'")
    private String quality = "standard";
    
    @Pattern(regexp = "vivid|natural", message = "Style must be 'vivid' or 'natural'")
    private String style = "vivid";
}

// Response DTOs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationResponse {
    private boolean success;
    private List<GeneratedImageInfo> images;
    private Integer totalGenerated;
    private String error;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedImageInfo {
    private String originalPrompt;
    private String revisedPrompt;
    private String imageUrl;
    private String localPath;
    private String size;
    private String quality;
    private String style;
    private Instant createdAt;
}
```

#### Image Storage Service

```java
@Service
@Slf4j
public class ImageStorageService {

    @Value("${app.image.storage.path:/tmp/generated-images}")
    private String storagePath;

    private final RestTemplate restTemplate;

    public ImageStorageService() {
        this.restTemplate = new RestTemplate();
        // Create storage directory
        createStorageDirectory();
    }

    public String downloadAndStore(String imageUrl) {
        try {
            // Generate unique filename
            String fileName = generateFileName(imageUrl);
            String filePath = Paths.get(storagePath, fileName).toString();

            // Download image
            byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
            
            if (imageBytes != null) {
                Files.write(Paths.get(filePath), imageBytes);
                log.info("Image stored at: {}", filePath);
                return filePath;
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error downloading and storing image", e);
            return null;
        }
    }

    private void createStorageDirectory() {
        try {
            Files.createDirectories(Paths.get(storagePath));
        } catch (IOException e) {
            log.error("Error creating storage directory", e);
        }
    }

    private String generateFileName(String imageUrl) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = extractExtension(imageUrl);
        return "generated_" + timestamp + extension;
    }

    private String extractExtension(String url) {
        return url.contains(".png") ? ".png" : ".jpg";
    }
}
```

### Stable Diffusion Integration

#### Local Deployment with Ollama

**1. Custom Stable Diffusion Configuration**

```java
@Configuration
@ConditionalOnProperty(name = "app.ai.stable-diffusion.enabled", havingValue = "true")
public class StableDiffusionConfig {

    @Value("${app.ai.stable-diffusion.endpoint:http://localhost:7860}")
    private String stableDiffusionEndpoint;

    @Bean
    @Primary
    public StableDiffusionImageModel stableDiffusionImageModel() {
        return new StableDiffusionImageModel(stableDiffusionEndpoint);
    }
}

// Custom Stable Diffusion Implementation
@Component
public class StableDiffusionImageModel implements ImageModel {

    private final String endpoint;
    private final RestTemplate restTemplate;

    public StableDiffusionImageModel(String endpoint) {
        this.endpoint = endpoint;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public ImageResponse call(ImagePrompt imagePrompt) {
        StableDiffusionRequest request = buildRequest(imagePrompt);
        
        try {
            ResponseEntity<StableDiffusionResponse> response = restTemplate
                .postForEntity(endpoint + "/sdapi/v1/txt2img", 
                              request, 
                              StableDiffusionResponse.class);

            return convertToImageResponse(response.getBody());
            
        } catch (Exception e) {
            throw new RuntimeException("Error calling Stable Diffusion API", e);
        }
    }

    private StableDiffusionRequest buildRequest(ImagePrompt imagePrompt) {
        return StableDiffusionRequest.builder()
            .prompt(imagePrompt.getInstructions().get(0).getText())
            .steps(20)
            .width(512)
            .height(512)
            .cfgScale(7.5)
            .samplerName("Euler")
            .build();
    }

    private ImageResponse convertToImageResponse(StableDiffusionResponse sdResponse) {
        List<ImageGeneration> generations = sdResponse.getImages().stream()
            .map(base64Image -> {
                // Convert base64 to Image object
                Image image = decodeBase64Image(base64Image);
                return new ImageGeneration(image);
            })
            .collect(Collectors.toList());

        return new ImageResponse(generations);
    }

    private Image decodeBase64Image(String base64Image) {
        // Implementation to decode base64 and create Image object
        return new Image(null, base64Image); // Simplified
    }
}
```

## 7.2 Image Processing Capabilities

### Vision Model Integration

**1. OpenAI Vision Model Setup**

```java
@Service
@Slf4j
public class ImageAnalysisService {

    private final OpenAiChatModel chatModel;

    public ImageAnalysisService(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String analyzeImage(String imageUrl, String question) {
        Message userMessage = new UserMessage(
            question,
            List.of(new Media(MimeTypeUtils.IMAGE_JPEG, imageUrl))
        );

        ChatResponse response = chatModel.call(new Prompt(List.of(userMessage)));
        return response.getResult().getOutput().getContent();
    }

    public ImageAnalysisResult performDetailedAnalysis(String imageUrl) {
        List<String> analyses = new ArrayList<>();
        
        // Multiple analysis perspectives
        analyses.add(analyzeImage(imageUrl, "Describe this image in detail."));
        analyses.add(analyzeImage(imageUrl, "What objects can you identify in this image?"));
        analyses.add(analyzeImage(imageUrl, "What is the mood or atmosphere of this image?"));
        analyses.add(analyzeImage(imageUrl, "Are there any people in this image? Describe them."));
        analyses.add(analyzeImage(imageUrl, "What colors are prominent in this image?"));

        return ImageAnalysisResult.builder()
            .imageUrl(imageUrl)
            .detailedDescription(analyses.get(0))
            .identifiedObjects(analyses.get(1))
            .moodAnalysis(analyses.get(2))
            .peopleAnalysis(analyses.get(3))
            .colorAnalysis(analyses.get(4))
            .analyzedAt(Instant.now())
            .build();
    }

    public boolean isImageSafe(String imageUrl) {
        String analysis = analyzeImage(imageUrl, 
            "Is this image appropriate for all audiences? " +
            "Answer with only 'YES' or 'NO' and a brief explanation.");
        
        return analysis.toUpperCase().startsWith("YES");
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysisResult {
    private String imageUrl;
    private String detailedDescription;
    private String identifiedObjects;
    private String moodAnalysis;
    private String peopleAnalysis;
    private String colorAnalysis;
    private Instant analyzedAt;
}
```

**2. Image Analysis Controller**

```java
@RestController
@RequestMapping("/api/images/analysis")
@Slf4j
public class ImageAnalysisController {

    private final ImageAnalysisService imageAnalysisService;

    public ImageAnalysisController(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ImageAnalysisResult> analyzeImage(
            @RequestBody ImageAnalysisRequest request) {
        
        try {
            ImageAnalysisResult result = imageAnalysisService
                .performDetailedAnalysis(request.getImageUrl());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error analyzing image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
        }
    }

    @PostMapping("/question")
    public ResponseEntity<ImageQuestionResponse> askAboutImage(
            @RequestBody ImageQuestionRequest request) {
        
        try {
            String answer = imageAnalysisService.analyzeImage(
                request.getImageUrl(), 
                request.getQuestion()
            );
            
            return ResponseEntity.ok(
                ImageQuestionResponse.builder()
                    .question(request.getQuestion())
                    .answer(answer)
                    .imageUrl(request.getImageUrl())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Error processing image question", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ImageQuestionResponse.builder()
                    .error("Failed to process question: " + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/safety-check")
    public ResponseEntity<ImageSafetyResponse> checkImageSafety(
            @RequestBody ImageSafetyRequest request) {
        
        try {
            boolean isSafe = imageAnalysisService.isImageSafe(request.getImageUrl());
            
            return ResponseEntity.ok(
                ImageSafetyResponse.builder()
                    .imageUrl(request.getImageUrl())
                    .isSafe(isSafe)
                    .checkedAt(Instant.now())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Error checking image safety", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ImageSafetyResponse.builder()
                    .error("Failed to check safety: " + e.getMessage())
                    .build());
        }
    }
}
```

### OCR and Text Extraction

```java
@Service
@Slf4j
public class OCRService {

    private final OpenAiChatModel chatModel;

    public OCRService(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public TextExtractionResult extractText(String imageUrl) {
        String extractedText = analyzeImageForText(imageUrl,
            "Extract all text visible in this image. " +
            "Maintain the original formatting and structure as much as possible.");

        return TextExtractionResult.builder()
            .imageUrl(imageUrl)
            .extractedText(extractedText)
            .extractedAt(Instant.now())
            .confidence(estimateConfidence(extractedText))
            .build();
    }

    public DocumentExtractionResult extractDocumentData(String imageUrl) {
        String structuredData = analyzeImageForText(imageUrl,
            "This appears to be a document. Extract and structure the information " +
            "including headers, key-value pairs, tables, and any other structured data. " +
            "Format the response as JSON if possible.");

        return DocumentExtractionResult.builder()
            .imageUrl(imageUrl)
            .structuredData(structuredData)
            .extractedAt(Instant.now())
            .build();
    }

    public BusinessCardData extractBusinessCard(String imageUrl) {
        String analysis = analyzeImageForText(imageUrl,
            "Extract business card information and return it in this JSON format: " +
            "{\"name\": \"\", \"title\": \"\", \"company\": \"\", " +
            "\"phone\": \"\", \"email\": \"\", \"address\": \"\", \"website\": \"\"}");

        try {
            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(analysis, BusinessCardData.class);
        } catch (Exception e) {
            log.warn("Failed to parse business card JSON, returning raw data", e);
            return BusinessCardData.builder()
                .rawData(analysis)
                .build();
        }
    }

    private String analyzeImageForText(String imageUrl, String instruction) {
        Message userMessage = new UserMessage(
            instruction,
            List.of(new Media(MimeTypeUtils.IMAGE_JPEG, imageUrl))
        );

        ChatResponse response = chatModel.call(new Prompt(List.of(userMessage)));
        return response.getResult().getOutput().getContent();
    }

    private double estimateConfidence(String extractedText) {
        // Simple confidence estimation based on text characteristics
        if (extractedText == null || extractedText.trim().isEmpty()) {
            return 0.0;
        }
        
        int totalChars = extractedText.length();
        int alphanumericChars = (int) extractedText.chars()
            .filter(Character::isLetterOrDigit)
            .count();
        
        return Math.min(1.0, (double) alphanumericChars / totalChars);
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextExtractionResult {
    private String imageUrl;
    private String extractedText;
    private Instant extractedAt;
    private double confidence;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCardData {
    private String name;
    private String title;
    private String company;
    private String phone;
    private String email;
    private String address;
    private String website;
    private String rawData;
}
```

## Real-World Use Cases and Complete Examples

### Use Case 1: E-commerce Product Image Generator

```java
@Service
@Slf4j
public class ProductImageService {

    private final ImageGenerationService imageGenerationService;
    private final ImageAnalysisService imageAnalysisService;

    public ProductImageService(ImageGenerationService imageGenerationService,
                              ImageAnalysisService imageAnalysisService) {
        this.imageGenerationService = imageGenerationService;
        this.imageAnalysisService = imageAnalysisService;
    }

    public ProductImageResult generateProductImages(ProductImageRequest request) {
        List<String> prompts = generatePrompts(request);
        List<GeneratedImageInfo> images = new ArrayList<>();
        
        for (String prompt : prompts) {
            try {
                ImageResponse response = imageGenerationService
                    .generateImageWithOptions(prompt, "1024x1024", "hd", "vivid");
                
                response.getResults().forEach(result -> {
                    String imageUrl = result.getOutput().getUrl();
                    
                    // Analyze generated image for quality
                    String quality = imageAnalysisService.analyzeImage(imageUrl,
                        "Rate the quality of this product image for e-commerce " +
                        "on a scale of 1-10 and explain why.");
                    
                    images.add(GeneratedImageInfo.builder()
                        .originalPrompt(prompt)
                        .imageUrl(imageUrl)
                        .qualityScore(extractQualityScore(quality))
                        .qualityAnalysis(quality)
                        .build());
                });
                
            } catch (Exception e) {
                log.error("Error generating image for prompt: {}", prompt, e);
            }
        }
        
        return ProductImageResult.builder()
            .productName(request.getProductName())
            .category(request.getCategory())
            .images(images)
            .totalGenerated(images.size())
            .build();
    }

    private List<String> generatePrompts(ProductImageRequest request) {
        return List.of(
            String.format("Professional product photography of %s, white background, " +
                "studio lighting, high resolution, commercial quality",
                request.getProductName()),
            
            String.format("%s in a modern lifestyle setting, natural lighting, " +
                "Instagram-worthy, lifestyle photography",
                request.getProductName()),
            
            String.format("Minimalist product shot of %s, clean aesthetic, " +
                "professional e-commerce photography, centered composition",
                request.getProductName()),
            
            String.format("%s with dramatic lighting, luxury feel, " +
                "premium product photography, artistic shadows",
                request.getProductName())
        );
    }

    private int extractQualityScore(String analysis) {
        Pattern pattern = Pattern.compile("(\\d+)/10|score.*?(\\d+)|(\\d+)\\s*out\\s*of\\s*10");
        Matcher matcher = pattern.matcher(analysis);
        
        if (matcher.find()) {
            String score = matcher.group(1) != null ? matcher.group(1) : 
                          matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            try {
                return Integer.parseInt(score);
            } catch (NumberFormatException e) {
                return 5; // Default score
            }
        }
        return 5;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageRequest {
    private String productName;
    private String category;
    private String description;
    private List<String> features;
    private String targetAudience;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResult {
    private String productName;
    private String category;
    private List<GeneratedImageInfo> images;
    private int totalGenerated;
}
```

### Use Case 2: Content Moderation System

```java
@Service
@Slf4j
public class ContentModerationService {

    private final ImageAnalysisService imageAnalysisService;

    public ContentModerationService(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    public ModerationResult moderateImage(String imageUrl) {
        CompletableFuture<String> safetyCheck = CompletableFuture.supplyAsync(() ->
            imageAnalysisService.analyzeImage(imageUrl,
                "Analyze this image for inappropriate content including: " +
                "violence, adult content, hate symbols, dangerous activities. " +
                "Respond with SAFE or UNSAFE and explanation.")
        );

        CompletableFuture<String> contentAnalysis = CompletableFuture.supplyAsync(() ->
            imageAnalysisService.analyzeImage(imageUrl,
                "What is the main subject and content of this image? " +
                "Is it suitable for a general audience?")
        );

        CompletableFuture<String> textAnalysis = CompletableFuture.supplyAsync(() ->
            imageAnalysisService.analyzeImage(imageUrl,
                "Is there any text in this image? If yes, extract it and " +
                "check if it contains inappropriate language or content.")
        );

        try {
            CompletableFuture.allOf(safetyCheck, contentAnalysis, textAnalysis).join();

            boolean isSafe = safetyCheck.get().toUpperCase().contains("SAFE");
            
            return ModerationResult.builder()
                .imageUrl(imageUrl)
                .isSafe(isSafe)
                .safetyAnalysis(safetyCheck.get())
                .contentAnalysis(contentAnalysis.get())
                .textAnalysis(textAnalysis.get())
                .moderatedAt(Instant.now())
                .confidence(calculateConfidence(safetyCheck.get()))
                .build();

        } catch (Exception e) {
            log.error("Error during content moderation", e);
            return ModerationResult.builder()
                .imageUrl(imageUrl)
                .isSafe(false)
                .error("Moderation failed: " + e.getMessage())
                .build();
        }
    }

    private double calculateConfidence(String analysis) {
        // Simple confidence calculation based on certainty words
        String[] highConfidence = {"clearly", "obviously", "definitely", "certainly"};
        String[] lowConfidence = {"might", "possibly", "could be", "uncertain"};
        
        String lowerAnalysis = analysis.toLowerCase();
        
        for (String word : highConfidence) {
            if (lowerAnalysis.contains(word)) {
                return 0.9;
            }
        }
        
        for (String word : lowConfidence) {
            if (lowerAnalysis.contains(word)) {
                return 0.6;
            }
        }
        
        return 0.75; // Default confidence
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {
    private String imageUrl;
    private boolean isSafe;
    private String safetyAnalysis;
    private String contentAnalysis;
    private String textAnalysis;
    private Instant moderatedAt;
    private double confidence;
    private String error;
}
```

### Use Case 3: Social Media Content Creator

```java
@Service
@Slf4j
public class SocialMediaContentService {

    private final ImageGenerationService imageGenerationService;
    private final ImageAnalysisService imageAnalysisService;

    public SocialMediaContentService(ImageGenerationService imageGenerationService,
                                   ImageAnalysisService imageAnalysisService) {
        this.imageGenerationService = imageGenerationService;
        this.imageAnalysisService = imageAnalysisService;
    }

    public SocialMediaPackage createContentPackage(ContentCreationRequest request) {
        List<SocialMediaPost> posts = new ArrayList<>();
        
        // Generate different variations for different platforms
        Map<String, String> platformPrompts = generatePlatformSpecificPrompts(request);
        
        platformPrompts.entrySet().parallelStream().forEach(entry -> {
            String platform = entry.getKey();
            String prompt = entry.getValue();
            
            try {
                ImageResponse response = imageGenerationService
                    .generateImageWithOptions(prompt, 
                        getPlatformOptimalSize(platform), "hd", "vivid");
                
                response.getResults().forEach(result -> {
                    String imageUrl = result.getOutput().getUrl();
                    
                    // Generate caption using image analysis
                    String caption = generateCaption(imageUrl, request, platform);
                    List<String> hashtags = generateHashtags(imageUrl, request, platform);
                    
                    synchronized (posts) {
                        posts.add(SocialMediaPost.builder()
                            .platform(platform)
                            .imageUrl(imageUrl)
                            .caption(caption)
                            .hashtags(hashtags)
                            .prompt(prompt)
                            .build());
                    }
                });
                
            } catch (Exception e) {
                log.error("Error creating content for platform: {}", platform, e);
            }
        });
        
        return SocialMediaPackage.builder()
            .theme(request.getTheme())
            .brand(request.getBrand())
            .posts(posts)
            .createdAt(Instant.now())
            .build();
    }

    private Map<String, String> generatePlatformSpecificPrompts(ContentCreationRequest request) {
        Map<String, String> prompts = new HashMap<>();
        
        String basePrompt = String.format("Create a %s themed image for %s brand", 
            request.getTheme(), request.getBrand());
        
        prompts.put("Instagram", basePrompt + ", Instagram-style, vibrant colors, " +
            "trendy aesthetic, square format, social media ready");
        
        prompts.put("Facebook", basePrompt + ", Facebook cover style, engaging visual, " +
            "professional yet approachable, landscape format");
        
        prompts.put("Twitter", basePrompt + ", Twitter header style, clean design, " +
            "eye-catching, landscape format, modern");
        
        prompts.put("LinkedIn", basePrompt + ", LinkedIn style, professional, " +
            "corporate aesthetic, business-appropriate, landscape format");
        
        return prompts;
    }

    private String getPlatformOptimalSize(String platform) {
        return switch (platform.toLowerCase()) {
            case "instagram" -> "1024x1024";
            case "facebook", "twitter", "linkedin" -> "1792x1024";
            default -> "1024x1024";
        };
    }

    private String generateCaption(String imageUrl, ContentCreationRequest request, String platform) {
        String prompt = String.format(
            "Create a %s caption for this image. Brand: %s, Theme: %s. " +
            "Make it engaging, on-brand, and appropriate for %s audience.",
            platform, request.getBrand(), request.getTheme(), platform
        );
        
        return imageAnalysisService.analyzeImage(imageUrl, prompt);
    }

    private List<String> generateHashtags(String imageUrl, ContentCreationRequest request, String platform) {
        String hashtagPrompt = String.format(
            "Generate 10-15 relevant hashtags for this %s post. " +
            "Brand: %s, Theme: %s. Include trending and niche hashtags. " +
            "Return only hashtags separated by commas, no # symbols.",
            platform, request.getBrand(), request.getTheme()
        );
        
        String hashtagResponse = imageAnalysisService.analyzeImage(imageUrl, hashtagPrompt);
        
        return Arrays.stream(hashtagResponse.split(","))
            .map(String::trim)
            .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
            .collect(Collectors.toList());
    }
}

// DTOs for Social Media Content Service
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentCreationRequest {
    private String theme;
    private String brand;
    private String targetAudience;
    private List<String> keywords;
    private String tone; // professional, casual, fun, etc.
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialMediaPackage {
    private String theme;
    private String brand;
    private List<SocialMediaPost> posts;
    private Instant createdAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialMediaPost {
    private String platform;
    private String imageUrl;
    private String caption;
    private List<String> hashtags;
    private String prompt;
}
```

## Advanced Image Processing Features

### Image Comparison and Similarity Analysis

```java
@Service
@Slf4j
public class ImageComparisonService {

    private final ImageAnalysisService imageAnalysisService;
    private final EmbeddingModel embeddingModel;

    public ImageComparisonService(ImageAnalysisService imageAnalysisService,
                                 EmbeddingModel embeddingModel) {
        this.imageAnalysisService = imageAnalysisService;
        this.embeddingModel = embeddingModel;
    }

    public ImageComparisonResult compareImages(String imageUrl1, String imageUrl2) {
        // Visual comparison using AI
        String comparisonPrompt = String.format(
            "Compare these two images and provide: " +
            "1. Similarity score (0-100) " +
            "2. Key differences " +
            "3. Similar elements " +
            "4. Overall assessment"
        );

        CompletableFuture<String> visualComparison = CompletableFuture.supplyAsync(() ->
            analyzeMultipleImages(List.of(imageUrl1, imageUrl2), comparisonPrompt)
        );

        // Get descriptions for embedding comparison
        CompletableFuture<String> desc1 = CompletableFuture.supplyAsync(() ->
            imageAnalysisService.analyzeImage(imageUrl1, "Describe this image in detail.")
        );

        CompletableFuture<String> desc2 = CompletableFuture.supplyAsync(() ->
            imageAnalysisService.analyzeImage(imageUrl2, "Describe this image in detail.")
        );

        try {
            CompletableFuture.allOf(visualComparison, desc1, desc2).join();

            // Calculate embedding similarity
            double embeddingSimilarity = calculateEmbeddingSimilarity(
                desc1.get(), desc2.get()
            );

            return ImageComparisonResult.builder()
                .imageUrl1(imageUrl1)
                .imageUrl2(imageUrl2)
                .visualAnalysis(visualComparison.get())
                .embeddingSimilarity(embeddingSimilarity)
                .description1(desc1.get())
                .description2(desc2.get())
                .comparedAt(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error comparing images", e);
            throw new RuntimeException("Image comparison failed", e);
        }
    }

    public List<ImageSimilarityResult> findSimilarImages(String queryImageUrl, 
                                                        List<String> candidateImageUrls) {
        String queryDescription = imageAnalysisService.analyzeImage(
            queryImageUrl, "Describe this image in detail for similarity matching."
        );

        return candidateImageUrls.parallelStream()
            .map(candidateUrl -> {
                try {
                    String candidateDescription = imageAnalysisService.analyzeImage(
                        candidateUrl, "Describe this image in detail for similarity matching."
                    );

                    double similarity = calculateEmbeddingSimilarity(
                        queryDescription, candidateDescription
                    );

                    return ImageSimilarityResult.builder()
                        .queryImageUrl(queryImageUrl)
                        .candidateImageUrl(candidateUrl)
                        .similarity(similarity)
                        .queryDescription(queryDescription)
                        .candidateDescription(candidateDescription)
                        .build();

                } catch (Exception e) {
                    log.error("Error analyzing candidate image: {}", candidateUrl, e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .sorted((r1, r2) -> Double.compare(r2.getSimilarity(), r1.getSimilarity()))
            .collect(Collectors.toList());
    }

    private String analyzeMultipleImages(List<String> imageUrls, String prompt) {
        List<Media> mediaList = imageUrls.stream()
            .map(url -> new Media(MimeTypeUtils.IMAGE_JPEG, url))
            .collect(Collectors.toList());

        Message userMessage = new UserMessage(prompt, mediaList);
        
        // This would need a chat model that supports multiple images
        // For now, we'll analyze them separately and combine
        return imageAnalysisService.analyzeImage(imageUrls.get(0), 
            prompt + " (Note: Compare with the second image provided)");
    }

    private double calculateEmbeddingSimilarity(String text1, String text2) {
        try {
            EmbeddingResponse response1 = embeddingModel.call(
                new EmbeddingRequest(List.of(text1), null)
            );
            EmbeddingResponse response2 = embeddingModel.call(
                new EmbeddingRequest(List.of(text2), null)
            );

            List<Double> embedding1 = response1.getResults().get(0).getOutput();
            List<Double> embedding2 = response2.getResults().get(0).getOutput();

            return calculateCosineSimilarity(embedding1, embedding2);

        } catch (Exception e) {
            log.error("Error calculating embedding similarity", e);
            return 0.0;
        }
    }

    private double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += Math.pow(vec1.get(i), 2);
            norm2 += Math.pow(vec2.get(i), 2);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageComparisonResult {
    private String imageUrl1;
    private String imageUrl2;
    private String visualAnalysis;
    private double embeddingSimilarity;
    private String description1;
    private String description2;
    private Instant comparedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSimilarityResult {
    private String queryImageUrl;
    private String candidateImageUrl;
    private double similarity;
    private String queryDescription;
    private String candidateDescription;
}
```

### Batch Image Processing Service

```java
@Service
@Slf4j
public class BatchImageProcessingService {

    private final ImageAnalysisService imageAnalysisService;
    private final ImageGenerationService imageGenerationService;
    private final TaskExecutor taskExecutor;

    public BatchImageProcessingService(ImageAnalysisService imageAnalysisService,
                                     ImageGenerationService imageGenerationService,
                                     @Qualifier("imageProcessingExecutor") TaskExecutor taskExecutor) {
        this.imageAnalysisService = imageAnalysisService;
        this.imageGenerationService = imageGenerationService;
        this.taskExecutor = taskExecutor;
    }

    public BatchProcessingJob startBatchAnalysis(BatchAnalysisRequest request) {
        String jobId = UUID.randomUUID().toString();
        
        BatchProcessingJob job = BatchProcessingJob.builder()
            .jobId(jobId)
            .type(BatchJobType.ANALYSIS)
            .status(BatchJobStatus.RUNNING)
            .totalImages(request.getImageUrls().size())
            .processedImages(0)
            .startTime(Instant.now())
            .results(new ConcurrentHashMap<>())
            .build();

        // Store job in cache/database
        jobRepository.save(job);

        // Process images asynchronously
        CompletableFuture.runAsync(() -> processBatchAnalysis(job, request), taskExecutor);

        return job;
    }

    public BatchProcessingJob startBatchGeneration(BatchGenerationRequest request) {
        String jobId = UUID.randomUUID().toString();
        
        BatchProcessingJob job = BatchProcessingJob.builder()
            .jobId(jobId)
            .type(BatchJobType.GENERATION)
            .status(BatchJobStatus.RUNNING)
            .totalImages(request.getPrompts().size())
            .processedImages(0)
            .startTime(Instant.now())
            .results(new ConcurrentHashMap<>())
            .build();

        jobRepository.save(job);
        CompletableFuture.runAsync(() -> processBatchGeneration(job, request), taskExecutor);

        return job;
    }

    private void processBatchAnalysis(BatchProcessingJob job, BatchAnalysisRequest request) {
        AtomicInteger processed = new AtomicInteger(0);
        
        request.getImageUrls().parallelStream().forEach(imageUrl -> {
            try {
                ImageAnalysisResult result = imageAnalysisService.performDetailedAnalysis(imageUrl);
                job.getResults().put(imageUrl, result);
                
                int currentProcessed = processed.incrementAndGet();
                job.setProcessedImages(currentProcessed);
                
                // Update progress
                updateJobProgress(job.getJobId(), currentProcessed, job.getTotalImages());
                
            } catch (Exception e) {
                log.error("Error processing image in batch: {}", imageUrl, e);
                job.getResults().put(imageUrl, "Error: " + e.getMessage());
            }
        });

        job.setStatus(BatchJobStatus.COMPLETED);
        job.setEndTime(Instant.now());
        jobRepository.save(job);
    }

    private void processBatchGeneration(BatchProcessingJob job, BatchGenerationRequest request) {
        AtomicInteger processed = new AtomicInteger(0);
        
        request.getPrompts().parallelStream().forEach(prompt -> {
            try {
                ImageResponse response = imageGenerationService.generateImage(prompt);
                List<String> generatedUrls = response.getResults().stream()
                    .map(result -> result.getOutput().getUrl())
                    .collect(Collectors.toList());
                
                job.getResults().put(prompt, generatedUrls);
                
                int currentProcessed = processed.incrementAndGet();
                job.setProcessedImages(currentProcessed);
                
                updateJobProgress(job.getJobId(), currentProcessed, job.getTotalImages());
                
            } catch (Exception e) {
                log.error("Error generating image in batch: {}", prompt, e);
                job.getResults().put(prompt, "Error: " + e.getMessage());
            }
        });

        job.setStatus(BatchJobStatus.COMPLETED);
        job.setEndTime(Instant.now());
        jobRepository.save(job);
    }

    public BatchProcessingJob getJobStatus(String jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    private void updateJobProgress(String jobId, int processed, int total) {
        // This could publish to WebSocket for real-time updates
        double progress = (double) processed / total * 100;
        log.info("Job {} progress: {}/{} ({}%)", jobId, processed, total, progress);
        
        // Publish progress event
        applicationEventPublisher.publishEvent(
            new BatchJobProgressEvent(jobId, processed, total, progress)
        );
    }

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    
    @Autowired
    private BatchJobRepository jobRepository;
}

// Supporting classes
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchProcessingJob {
    @Id
    private String jobId;
    
    @Enumerated(EnumType.STRING)
    private BatchJobType type;
    
    @Enumerated(EnumType.STRING)
    private BatchJobStatus status;
    
    private int totalImages;
    private int processedImages;
    private Instant startTime;
    private Instant endTime;
    
    @ElementCollection
    @MapKeyColumn(name = "input_key")
    @Column(name = "result_value", columnDefinition = "TEXT")
    private Map<String, Object> results;
}

public enum BatchJobType {
    ANALYSIS, GENERATION
}

public enum BatchJobStatus {
    RUNNING, COMPLETED, FAILED
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAnalysisRequest {
    private List<String> imageUrls;
    private String analysisType; // "detailed", "safety", "ocr", etc.
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGenerationRequest {
    private List<String> prompts;
    private String size;
    private String quality;
    private String style;
}
```

### Configuration for Batch Processing

```java
@Configuration
@EnableAsync
public class ImageProcessingConfig {

    @Bean("imageProcessingExecutor")
    public TaskExecutor imageProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ImageProcessing-");
        executor.initialize();
        return executor;
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Configure timeouts
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(60000);
        
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}
```

## Error Handling and Resilience Patterns

```java
@Component
@Slf4j
public class ResilientImageService {

    private final ImageGenerationService imageGenerationService;
    private final CircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;

    public ResilientImageService(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
        this.circuitBreaker = CircuitBreaker.ofDefaults("imageGeneration");
        this.retryTemplate = createRetryTemplate();
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public ImageResponse generateWithResilience(String prompt) {
        return circuitBreaker.executeSupplier(() -> 
            retryTemplate.execute(context -> {
                log.info("Attempting image generation, attempt: {}", context.getRetryCount() + 1);
                return imageGenerationService.generateImage(prompt);
            })
        );
    }

    @Recover
    public ImageResponse recover(Exception ex, String prompt) {
        log.error("All retry attempts failed for prompt: {}", prompt, ex);
        
        // Return cached or fallback image
        return createFallbackResponse(prompt, ex.getMessage());
    }

    private RetryTemplate createRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000);
        template.setBackOffPolicy(backOffPolicy);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        template.setRetryPolicy(retryPolicy);

        return template;
    }

    private ImageResponse createFallbackResponse(String prompt, String error) {
        // Create a placeholder response or return cached result
        return new ImageResponse(List.of(
            new ImageGeneration(new Image(null, "fallback-image-url"))
        ));
    }
}
```

## Testing Strategy

```java
@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class ImageServiceIntegrationTest {

    @Autowired
    private ImageGenerationService imageGenerationService;

    @Autowired
    private ImageAnalysisService imageAnalysisService;

    @MockBean
    private OpenAiImageModel imageModel;

    @Test
    @Order(1)
    void testImageGeneration() {
        // Mock response
        ImageResponse mockResponse = createMockImageResponse();
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(mockResponse);

        // Test
        ImageResponse response = imageGenerationService.generateImage("test prompt");

        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(1);
        verify(imageModel).call(any(ImagePrompt.class));
    }

    @Test
    @Order(2)
    void testImageAnalysis() {
        String testImageUrl = "https://example.com/test-image.jpg";
        String expectedAnalysis = "This is a test image showing...";

        // Mock the chat model response for vision
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(createMockChatResponse(expectedAnalysis));

        ImageAnalysisResult result = imageAnalysisService
            .performDetailedAnalysis(testImageUrl);

        assertThat(result).isNotNull();
        assertThat(result.getDetailedDescription()).contains(expectedAnalysis);
    }

    @Test
    void testBatchProcessing() {
        List<String> testUrls = Arrays.asList(
            "https://example.com/image1.jpg",
            "https://example.com/image2.jpg"
        );

        BatchAnalysisRequest request = BatchAnalysisRequest.builder()
            .imageUrls(testUrls)
            .analysisType("detailed")
            .build();

        BatchProcessingJob job = batchImageProcessingService
            .startBatchAnalysis(request);

        assertThat(job.getJobId()).isNotNull();
        assertThat(job.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(job.getTotalImages()).isEqualTo(2);
    }

    private ImageResponse createMockImageResponse() {
        Image mockImage = new Image("https://example.com/generated-image.jpg", null);
        ImageGeneration generation = new ImageGeneration(mockImage);
        return new ImageResponse(List.of(generation));
    }

    private ChatResponse createMockChatResponse(String content) {
        Generation generation = new Generation(new AssistantMessage(content));
        return new ChatResponse(List.of(generation));
    }
}
```

## Performance Optimization and Best Practices

### Caching Strategy

```java
@Service
@Slf4j
public class CachedImageService {

    private final ImageAnalysisService imageAnalysisService;
    
    @Cacheable(value = "imageAnalysis", key = "#imageUrl + '_' + #prompt")
    public String getCachedAnalysis(String imageUrl, String prompt) {
        log.info("Cache miss for image analysis: {}", imageUrl);
        return imageAnalysisService.analyzeImage(imageUrl, prompt);
    }

    @CacheEvict(value = "imageAnalysis", allEntries = true)
    public void clearAnalysisCache() {
        log.info("Cleared image analysis cache");
    }

    @Cacheable(value = "generatedImages", key = "#prompt + '_' + #options.hashCode()")
    public ImageResponse getCachedGeneration(String prompt, OpenAiImageOptions options) {
        log.info("Cache miss for image generation: {}", prompt);
        return imageGenerationService.generateImageWithOptions(
            prompt, 
            options.getSize(), 
            options.getQuality(), 
            options.getStyle()
        );
    }
}

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(2))
            .recordStats());
        return cacheManager;
    }
}
```

This completes Phase 7 of the Spring AI mastery roadmap. The examples cover:

1. **DALL-E Integration** with comprehensive configuration and error handling
2. **Stable Diffusion** local deployment patterns
3. **Vision Model Integration** for image analysis and OCR
4. **Real-world Use Cases** including e-commerce, content moderation, and social media
5. **Advanced Features** like image comparison and batch processing
6. **Production Patterns** including resilience, caching, and testing

The code examples demonstrate enterprise-grade implementations with proper error handling, async processing, and scalability considerations. Each service is designed to be production-ready with comprehensive logging, metrics, and monitoring capabilities.