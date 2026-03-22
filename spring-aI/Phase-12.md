# Phase 12: Real-world Projects and Case Studies
## Complete Implementation Guide with Code Examples

---

## 12.1 Conversational AI Chatbot System

### Architecture Overview

The chatbot system will feature:
- Context-aware conversations with memory
- Intent recognition and entity extraction
- Multi-turn conversation handling
- Integration with business systems
- Personalization based on user profiles

### Core Components Implementation

#### 1. Conversation Context Manager

```java
@Component
public class ConversationContextManager {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public ConversationContextManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }
    
    public void saveContext(String conversationId, ConversationContext context) {
        try {
            String contextJson = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(
                "conversation:" + conversationId, 
                contextJson, 
                Duration.ofHours(24)
            );
        } catch (Exception e) {
            log.error("Failed to save conversation context", e);
        }
    }
    
    public Optional<ConversationContext> getContext(String conversationId) {
        try {
            String contextJson = (String) redisTemplate.opsForValue()
                .get("conversation:" + conversationId);
            if (contextJson != null) {
                return Optional.of(objectMapper.readValue(contextJson, ConversationContext.class));
            }
        } catch (Exception e) {
            log.error("Failed to retrieve conversation context", e);
        }
        return Optional.empty();
    }
}
```

#### 2. Intent Recognition Service

```java
@Service
public class IntentRecognitionService {
    private final ChatModel chatModel;
    private final PromptTemplate intentPrompt;
    
    public IntentRecognitionService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.intentPrompt = new PromptTemplate("""
            Analyze the user message and determine the intent and extract entities.
            
            User message: {message}
            Previous context: {context}
            
            Respond in JSON format:
            {
                "intent": "intent_name",
                "confidence": 0.95,
                "entities": {
                    "entity_type": "entity_value"
                },
                "requires_clarification": false
            }
            
            Possible intents: greeting, question, request_support, book_appointment, 
            check_status, complaint, compliment, goodbye
            """);
    }
    
    public IntentResult recognizeIntent(String message, ConversationContext context) {
        Map<String, Object> promptVariables = Map.of(
            "message", message,
            "context", context.getSummary()
        );
        
        Prompt prompt = intentPrompt.create(promptVariables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                CodeOptimizationResult.class
            );
        } catch (Exception e) {
            log.error("Failed to parse code optimization result", e);
            return CodeOptimizationResult.defaultResult();
        }
    }
    
    public List<CodeSuggestion> generateCodeSuggestions(String partialCode, 
                                                       String language, 
                                                       String context) {
        
        // Search for similar code patterns in the codebase
        List<Document> similarCodeChunks = findSimilarCodePatterns(partialCode, language);
        
        PromptTemplate suggestionTemplate = new PromptTemplate("""
            Based on the partial code and similar patterns from the codebase,
            provide intelligent code completion suggestions.
            
            Partial Code:
            ```{language}
            {partialCode}
            ```
            
            Context: {context}
            
            Similar patterns from codebase:
            {similarPatterns}
            
            Provide 3-5 completion suggestions in JSON format:
            [
                {
                    "completion": "suggested code completion",
                    "description": "What this completion does",
                    "confidence": 0.95,
                    "reasoning": "Why this suggestion makes sense"
                }
            ]
            """);
        
        String similarPatterns = similarCodeChunks.stream()
            .map(doc -> doc.getContent())
            .collect(Collectors.joining("\n---\n"));
        
        Map<String, Object> variables = Map.of(
            "language", language,
            "partialCode", partialCode,
            "context", context,
            "similarPatterns", similarPatterns
        );
        
        Prompt prompt = suggestionTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            TypeReference<List<CodeSuggestion>> typeRef = new TypeReference<List<CodeSuggestion>>() {};
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                typeRef
            );
        } catch (Exception e) {
            log.error("Failed to parse code suggestions", e);
            return Collections.emptyList();
        }
    }
    
    private List<Document> findSimilarCodePatterns(String code, String language) {
        SearchRequest searchRequest = SearchRequest.query(code)
            .withTopK(5)
            .withSimilarityThreshold(0.7)
            .withFilterExpression(Filter.Expression.eq("language", language));
        
        return codebaseVectorStore.similaritySearch(searchRequest);
    }
}
```

#### 2. Documentation Generation Service

```java
@Service
public class DocumentationGenerationService {
    private final ChatModel chatModel;
    private final CodeAnalysisService codeAnalysisService;
    
    public GeneratedDocumentation generateDocumentation(DocumentationRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        DocumentationType docType = request.getDocumentationType();
        
        // First analyze the code to understand its structure
        CodeAnalysisResult analysis = codeAnalysisService.analyzeCode(
            CodeAnalysisRequest.builder()
                .code(code)
                .language(language)
                .context("Documentation generation")
                .build()
        );
        
        PromptTemplate docTemplate = createDocumentationTemplate(docType);
        
        Map<String, Object> variables = Map.of(
            "language", language,
            "code", code,
            "analysis", objectMapper.writeValueAsString(analysis),
            "docType", docType.name(),
            "includeExamples", request.isIncludeExamples(),
            "targetAudience", request.getTargetAudience()
        );
        
        Prompt prompt = docTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        String documentation = response.getResult().getOutput().getContent();
        
        return GeneratedDocumentation.builder()
            .content(documentation)
            .documentationType(docType)
            .language(language)
            .generatedAt(LocalDateTime.now())
            .codeAnalysis(analysis)
            .build();
    }
    
    private PromptTemplate createDocumentationTemplate(DocumentationType docType) {
        switch (docType) {
            case API_DOCUMENTATION:
                return new PromptTemplate("""
                    Generate comprehensive API documentation for the following {language} code:
                    
                    Code:
                    ```{language}
                    {code}
                    ```
                    
                    Code Analysis: {analysis}
                    
                    Create documentation that includes:
                    1. **Overview**: Brief description of the API's purpose
                    2. **Authentication**: Required authentication methods
                    3. **Endpoints**: Detailed endpoint documentation with:
                       - HTTP method and URL
                       - Request parameters (path, query, body)
                       - Request/response examples
                       - Possible error codes and responses
                    4. **Data Models**: Schema definitions for request/response objects
                    5. **Error Handling**: Common error scenarios and solutions
                    6. **Rate Limiting**: Usage limits and throttling information
                    7. **SDKs**: Available client libraries and integration examples
                    
                    Format: Use Markdown with proper headers and code blocks.
                    Include realistic examples with sample data.
                    """);
                
            case USER_GUIDE:
                return new PromptTemplate("""
                    Create a user-friendly guide for the following {language} code:
                    
                    Code:
                    ```{language}
                    {code}
                    ```
                    
                    Target Audience: {targetAudience}
                    
                    Generate a comprehensive user guide with:
                    1. **Getting Started**: Installation and setup instructions
                    2. **Quick Start**: Simple example to get users started
                    3. **Core Features**: Main functionality with step-by-step tutorials
                    4. **Advanced Usage**: Complex scenarios and best practices
                    5. **Configuration**: Customization options and settings
                    6. **Troubleshooting**: Common issues and solutions
                    7. **FAQ**: Frequently asked questions
                    8. **Examples**: Real-world use cases and code samples
                    
                    Use clear, non-technical language where possible.
                    Include screenshots placeholders [Screenshot: description].
                    """);
                
            case TECHNICAL_SPECIFICATION:
                return new PromptTemplate("""
                    Create detailed technical specifications for the following {language} code:
                    
                    Code:
                    ```{language}
                    {code}
                    ```
                    
                    Code Analysis: {analysis}
                    
                    Generate technical specs including:
                    1. **Architecture Overview**: High-level system design
                    2. **Component Specifications**: Detailed component descriptions
                    3. **Data Flow**: How data moves through the system
                    4. **Database Schema**: Data models and relationships
                    5. **Security Considerations**: Security measures and requirements
                    6. **Performance Requirements**: Expected performance metrics
                    7. **Integration Points**: External systems and APIs
                    8. **Deployment Architecture**: Infrastructure requirements
                    9. **Monitoring & Logging**: Observability requirements
                    10. **Testing Strategy**: Unit, integration, and system testing
                    
                    Use technical diagrams descriptions and precise specifications.
                    """);
                
            default:
                return new PromptTemplate("""
                    Generate comprehensive documentation for the following {language} code:
                    
                    Code:
                    ```{language}
                    {code}
                    ```
                    
                    Include all relevant documentation sections based on the code type.
                    """);
        }
    }
    
    public TestGenerationResult generateTests(TestGenerationRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        String testFramework = request.getTestFramework();
        
        PromptTemplate testTemplate = new PromptTemplate("""
            Generate comprehensive unit tests for the following {language} code using {testFramework}:
            
            Code to test:
            ```{language}
            {code}
            ```
            
            Generate tests that cover:
            1. **Happy Path**: Normal execution scenarios
            2. **Edge Cases**: Boundary conditions and limits
            3. **Error Cases**: Exception handling and invalid inputs
            4. **Performance**: Load testing for critical methods
            5. **Integration**: Testing with dependencies (using mocks)
            
            Test Requirements:
            - Use {testFramework} framework conventions
            - Include setup and teardown methods
            - Use descriptive test method names
            - Add comments explaining complex test scenarios
            - Mock external dependencies appropriately
            - Include assertion messages for better debugging
            
            Provide the complete test class with imports and annotations.
            """);
        
        Map<String, Object> variables = Map.of(
            "language", language,
            "code", code,
            "testFramework", testFramework
        );
        
        Prompt prompt = testTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        String testCode = response.getResult().getOutput().getContent();
        
        // Extract test method information
        List<TestMethod> testMethods = extractTestMethods(testCode, testFramework);
        
        return TestGenerationResult.builder()
            .testCode(testCode)
            .testFramework(testFramework)
            .testMethods(testMethods)
            .coverageEstimate(calculateCoverageEstimate(testMethods))
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    private List<TestMethod> extractTestMethods(String testCode, String framework) {
        // Parse test code and extract test method information
        // This is a simplified version - in practice, you'd use AST parsing
        List<TestMethod> methods = new ArrayList<>();
        
        String[] lines = testCode.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.contains("@Test") || line.contains("test")) {
                // Extract test method details
                if (i + 1 < lines.length) {
                    String methodLine = lines[i + 1].trim();
                    String methodName = extractMethodName(methodLine);
                    if (methodName != null) {
                        methods.add(TestMethod.builder()
                            .name(methodName)
                            .type(determineTestType(methodName))
                            .lineNumber(i + 2)
                            .build());
                    }
                }
            }
        }
        
        return methods;
    }
    
    private String extractMethodName(String methodLine) {
        // Simple regex to extract method name
        Pattern pattern = Pattern.compile(".*\\s+(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(methodLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private TestType determineTestType(String methodName) {
        String lowerName = methodName.toLowerCase();
        if (lowerName.contains("exception") || lowerName.contains("error") || lowerName.contains("invalid")) {
            return TestType.ERROR_CASE;
        } else if (lowerName.contains("edge") || lowerName.contains("boundary") || lowerName.contains("limit")) {
            return TestType.EDGE_CASE;
        } else if (lowerName.contains("performance") || lowerName.contains("load")) {
            return TestType.PERFORMANCE;
        } else if (lowerName.contains("integration")) {
            return TestType.INTEGRATION;
        } else {
            return TestType.HAPPY_PATH;
        }
    }
    
    private double calculateCoverageEstimate(List<TestMethod> testMethods) {
        // Simple heuristic for coverage estimate
        long happyPathTests = testMethods.stream().filter(t -> t.getType() == TestType.HAPPY_PATH).count();
        long errorTests = testMethods.stream().filter(t -> t.getType() == TestType.ERROR_CASE).count();
        long edgeTests = testMethods.stream().filter(t -> t.getType() == TestType.EDGE_CASE).count();
        
        double coverage = 0.0;
        coverage += Math.min(happyPathTests * 30, 50); // Max 50% for happy path
        coverage += Math.min(errorTests * 20, 30);     // Max 30% for error cases
        coverage += Math.min(edgeTests * 15, 20);      // Max 20% for edge cases
        
        return Math.min(coverage, 95.0); // Cap at 95%
    }
}
```

#### 3. Code Assistant REST Controller

```java
@RestController
@RequestMapping("/api/v1/code-assistant")
@SecurityRequirement(name = "bearerAuth")
public class CodeAssistantController {
    private final CodeAnalysisService analysisService;
    private final DocumentationGenerationService docService;
    private final CodeReviewService reviewService;
    
    @PostMapping("/analyze")
    public ResponseEntity<CodeAnalysisResult> analyzeCode(@RequestBody CodeAnalysisRequest request) {
        
        // Validate input
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (request.getCode().length() > 50000) {
            return ResponseEntity.badRequest()
                .body(CodeAnalysisResult.error("Code too large. Maximum 50,000 characters allowed."));
        }
        
        try {
            CodeAnalysisResult result = analysisService.analyzeCode(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Code analysis failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CodeAnalysisResult.error("Analysis failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/optimize")
    public ResponseEntity<CodeOptimizationResult> optimizeCode(@RequestBody CodeOptimizationRequest request) {
        try {
            CodeOptimizationResult result = analysisService.optimizeCode(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Code optimization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CodeOptimizationResult.error("Optimization failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/suggest")
    public ResponseEntity<List<CodeSuggestion>> getSuggestions(@RequestBody CodeSuggestionRequest request) {
        try {
            List<CodeSuggestion> suggestions = analysisService.generateCodeSuggestions(
                request.getPartialCode(),
                request.getLanguage(),
                request.getContext()
            );
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Code suggestion failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/generate-docs")
    public ResponseEntity<GeneratedDocumentation> generateDocumentation(@RequestBody DocumentationRequest request) {
        try {
            GeneratedDocumentation docs = docService.generateDocumentation(request);
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            log.error("Documentation generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/generate-tests")
    public ResponseEntity<TestGenerationResult> generateTests(@RequestBody TestGenerationRequest request) {
        try {
            TestGenerationResult result = docService.generateTests(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Test generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/review")
    public ResponseEntity<CodeReviewResult> reviewCode(@RequestBody CodeReviewRequest request) {
        try {
            CodeReviewResult review = reviewService.performCodeReview(request);
            return ResponseEntity.ok(review);
        } catch (Exception e) {
            log.error("Code review failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

---

## 12.4 Multi-modal Content Generation Platform

### Content Generation Architecture

#### 1. Content Strategy Service

```java
@Service
public class ContentStrategyService {
    private final ChatModel chatModel;
    private final ImageModel imageModel;
    private final MarketResearchService marketResearchService;
    private final ContentPerformanceService performanceService;
    
    public ContentStrategy generateContentStrategy(ContentStrategyRequest request) {
        
        // Step 1: Market research and audience analysis
        MarketResearchResult marketData = marketResearchService.analyzeMarket(
            request.getTargetAudience(),
            request.getIndustry(),
            request.getCompetitors()
        );
        
        // Step 2: Generate content strategy
        PromptTemplate strategyTemplate = new PromptTemplate("""
            Create a comprehensive content strategy based on the following information:
            
            Business Context:
            - Industry: {industry}
            - Target Audience: {targetAudience}
            - Business Goals: {businessGoals}
            - Brand Voice: {brandVoice}
            - Budget: {budget}
            - Timeline: {timeline}
            
            Market Research Data:
            {marketData}
            
            Generate a detailed content strategy in JSON format:
            {
                "strategy_overview": {
                    "mission": "Content mission statement",
                    "objectives": ["objective1", "objective2"],
                    "key_messaging": ["message1", "message2"],
                    "unique_value_proposition": "What makes this content unique"
                },
                "audience_segments": [
                    {
                        "name": "Primary Audience",
                        "demographics": "Age, location, interests",
                        "pain_points": ["pain1", "pain2"],
                        "content_preferences": ["blog", "video", "infographic"],
                        "channels": ["social", "email", "website"]
                    }
                ],
                "content_pillars": [
                    {
                        "pillar": "Educational",
                        "description": "Focus area description",
                        "content_types": ["how-to", "tutorials", "guides"],
                        "percentage": 40
                    }
                ],
                "content_calendar": {
                    "posting_frequency": "3 times per week",
                    "optimal_times": ["9 AM EST", "2 PM EST"],
                    "seasonal_considerations": ["holiday campaigns", "industry events"]
                },
                "performance_metrics": [
                    {
                        "metric": "engagement_rate",
                        "target": "5% increase",
                        "tracking_method": "social media analytics"
                    }
                ]
            }
            """);
        
        Map<String, Object> variables = Map.of(
            "industry", request.getIndustry(),
            "targetAudience", request.getTargetAudience(),
            "businessGoals", String.join(", ", request.getBusinessGoals()),
            "brandVoice", request.getBrandVoice(),
            "budget", request.getBudget(),
            "timeline", request.getTimeline(),
            "marketData", objectMapper.writeValueAsString(marketData)
        );
        
        Prompt prompt = strategyTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                ContentStrategy.class
            );
        } catch (Exception e) {
            log.error("Failed to parse content strategy", e);
            return ContentStrategy.defaultStrategy();
        }
    }
    
    public List<ContentIdea> generateContentIdeas(ContentIdeaRequest request) {
        ContentStrategy strategy = request.getStrategy();
        String contentPillar = request.getContentPillar();
        int numberOfIdeas = request.getNumberOfIdeas();
        
        PromptTemplate ideaTemplate = new PromptTemplate("""
            Generate {numberOfIdeas} creative content ideas for the "{contentPillar}" pillar
            based on the following strategy:
            
            Content Strategy: {strategy}
            Target Audience: {targetAudience}
            Brand Voice: {brandVoice}
            Current Trends: {trends}
            
            For each idea, provide:
            - Title/Headline
            - Content type (blog, video, infographic, etc.)
            - Key message
            - Target audience segment
            - Estimated engagement potential
            - Production complexity (low/medium/high)
            - Suggested call-to-action
            
            Format as JSON array:
            [
                {
                    "title": "Compelling headline",
                    "content_type": "blog_post",
                    "description": "Brief description of the content",
                    "key_message": "Main takeaway for audience",
                    "target_segment": "primary_audience",
                    "engagement_potential": "high",
                    "production_complexity": "medium",
                    "call_to_action": "Subscribe to our newsletter",
                    "keywords": ["keyword1", "keyword2"],
                    "estimated_reach": 5000
                }
            ]
            """);
        
        // Get current trends
        List<String> trends = marketResearchService.getCurrentTrends(request.getIndustry());
        
        Map<String, Object> variables = Map.of(
            "numberOfIdeas", numberOfIdeas,
            "contentPillar", contentPillar,
            "strategy", objectMapper.writeValueAsString(strategy),
            "targetAudience", request.getTargetAudience(),
            "brandVoice", request.getBrandVoice(),
            "trends", String.join(", ", trends)
        );
        
        Prompt prompt = ideaTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            TypeReference<List<ContentIdea>> typeRef = new TypeReference<List<ContentIdea>>() {};
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                typeRef
            );
        } catch (Exception e) {
            log.error("Failed to parse content ideas", e);
            return Collections.emptyList();
        }
    }
}
```

#### 2. Multi-modal Content Generator

```java
@Service
public class MultiModalContentGenerator {
    private final ChatModel chatModel;
    private final ImageModel imageModel;
    private final ContentOptimizationService optimizationService;
    
    public GeneratedContent generateContent(ContentGenerationRequest request) {
        ContentIdea idea = request.getContentIdea();
        ContentFormat format = request.getFormat();
        
        // Generate text content
        String textContent = generateTextContent(idea, format, request.getAdditionalRequirements());
        
        // Generate visual elements if needed
        List<GeneratedImage> images = new ArrayList<>();
        if (request.isIncludeImages()) {
            images = generateImages(idea, textContent, request.getImageRequirements());
        }
        
        // Generate social media variants
        List<SocialMediaVariant> socialVariants = new ArrayList<>();
        if (request.isIncludeSocialVariants()) {
            socialVariants = generateSocialMediaVariants(textContent, idea, request.getSocialPlatforms());
        }
        
        // Generate SEO metadata
        SEOMetadata seoMetadata = generateSEOMetadata(textContent, idea);
        
        // Perform content optimization
        ContentOptimizationResult optimization = optimizationService.optimizeContent(
            textContent, request.getTargetAudience(), request.getGoals()
        );
        
        return GeneratedContent.builder()
            .textContent(textContent)
            .images(images)
            .socialVariants(socialVariants)
            .seoMetadata(seoMetadata)
            .optimization(optimization)
            .contentIdea(idea)
            .generatedAt(LocalDateTime.now())
            .build();
    }
    
    private String generateTextContent(ContentIdea idea, ContentFormat format, 
                                     Map<String, String> additionalRequirements) {
        
        PromptTemplate contentTemplate = createContentTemplate(format);
        
        Map<String, Object> variables = Map.of(
            "title", idea.getTitle(),
            "description", idea.getDescription(),
            "keyMessage", idea.getKeyMessage(),
            "targetAudience", idea.getTargetSegment(),
            "callToAction", idea.getCallToAction(),
            "keywords", String.join(", ", idea.getKeywords()),
            "additionalRequirements", objectMapper.writeValueAsString(additionalRequirements)
        );
        
        Prompt prompt = contentTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }
    
    private PromptTemplate createContentTemplate(ContentFormat format) {
        switch (format) {
            case BLOG_POST:
                return new PromptTemplate("""
                    Write a comprehensive blog post with the following specifications:
                    
                    Title: {title}
                    Description: {description}
                    Key Message: {keyMessage}
                    Target Audience: {targetAudience}
                    Keywords to include: {keywords}
                    Call to Action: {callToAction}
                    Additional Requirements: {additionalRequirements}
                    
                    Structure the blog post as follows:
                    1. **Engaging Introduction** (hook the reader immediately)
                    2. **Main Content Sections** (3-5 sections with clear headings)
                    3. **Practical Examples** (real-world applications)
                    4. **Key Takeaways** (summarize main points)
                    5. **Strong Conclusion** with the call to action
                    
                    Writing Guidelines:
                    - Use conversational yet professional tone
                    - Include relevant statistics or data points
                    - Add subheadings for better readability
                    - Incorporate the keywords naturally
                    - Aim for 1500-2500 words
                    - Include internal linking suggestions [Link: topic]
                    
                    Blog Post:
                    """);
                    
            case SOCIAL_MEDIA_POST:
                return new PromptTemplate("""
                    Create an engaging social media post:
                    
                    Topic: {title}
                    Key Message: {keyMessage}
                    Target Audience: {targetAudience}
                    Call to Action: {callToAction}
                    
                    Requirements:
                    - Platform-optimized length
                    - Include relevant hashtags
                    - Engaging hook in first line
                    - Clear call to action
                    - Visual content suggestions
                    
                    Post Content:
                    """);
                    
            case EMAIL_NEWSLETTER:
                return new PromptTemplate("""
                    Write an email newsletter with the following elements:
                    
                    Subject Line Ideas (3 variations):
                    Main Content: {description}
                    Key Message: {keyMessage}
                    Target Audience: {targetAudience}
                    Call to Action: {callToAction}
                    
                    Email Structure:
                    1. **Subject Line** (compelling and clear)
                    2. **Preheader Text** (complementary to subject)
                    3. **Opening** (personal greeting)
                    4. **Main Content** (valuable information)
                    5. **Clear CTA** (prominent button/link)
                    6. **Footer** (unsubscribe, social links)
                    
                    Email Content:
                    """);
                    
            case LANDING_PAGE:
                return new PromptTemplate("""
                    Create compelling landing page copy:
                    
                    Page Goal: {keyMessage}
                    Target Audience: {targetAudience}
                    Primary CTA: {callToAction}
                    
                    Landing Page Sections:
                    1. **Hero Section** (headline, subheadline, hero CTA)
                    2. **Value Proposition** (benefits and features)
                    3. **Social Proof** (testimonials, reviews, logos)
                    4. **Feature Details** (detailed benefits)
                    5. **FAQ Section** (address common concerns)
                    6. **Final CTA Section** (urgency and action)
                    
                    Copy should be:
                    - Benefit-focused rather than feature-focused
                    - Scannable with bullet points and short paragraphs
                    - Include specific value propositions
                    - Address potential objections
                    
                    Landing Page Copy:
                    """);
                    
            default:
                return new PromptTemplate("""
                    Create content based on the following:
                    Title: {title}
                    Description: {description}
                    Key Message: {keyMessage}
                    Target Audience: {targetAudience}
                    Call to Action: {callToAction}
                    """);
        }
    }
    
    private List<GeneratedImage> generateImages(ContentIdea idea, String textContent, 
                                               ImageRequirements imageRequirements) {
        List<GeneratedImage> images = new ArrayList<>();
        
        // Extract key concepts from text for image generation
        List<String> imagePrompts = extractImagePrompts(textContent, idea);
        
        for (String imagePrompt : imagePrompts) {
            try {
                ImagePrompt prompt = new ImagePrompt(
                    enhanceImagePrompt(imagePrompt, imageRequirements),
                    ImageOptionsBuilder.builder()
                        .withModel("dall-e-3")
                        .withWidth(imageRequirements.getWidth())
                        .withHeight(imageRequirements.getHeight())
                        .withQuality("hd")
                        .build()
                );
                
                ImageResponse response = imageModel.call(prompt);
                Image generatedImage = response.getResult().getOutput();
                
                images.add(GeneratedImage.builder()
                    .url(generatedImage.getUrl())
                    .prompt(imagePrompt)
                    .altText(generateAltText(imagePrompt))
                    .width(imageRequirements.getWidth())
                    .height(imageRequirements.getHeight())
                    .build());
                
            } catch (Exception e) {
                log.error("Failed to generate image for prompt: " + imagePrompt, e);
            }
        }
        
        return images;
    }
    
    private List<String> extractImagePrompts(String textContent, ContentIdea idea) {
        PromptTemplate extractTemplate = new PromptTemplate("""
            Analyze the following content and suggest 2-3 image prompts that would
            enhance the content visually:
            
            Content: {textContent}
            Topic: {topic}
            Target Audience: {audience}
            
            For each image, provide a detailed prompt that would generate
            a professional, relevant image. Consider:
            - Visual metaphors for abstract concepts
            - Illustrations that support key points
            - Hero images for main topics
            - Infographic-style visuals for data
            
            Return as JSON array:
            ["detailed image prompt 1", "detailed image prompt 2", "detailed image prompt 3"]
            """);
        
        Map<String, Object> variables = Map.of(
            "textContent", textContent.length() > 2000 ? textContent.substring(0, 2000) + "..." : textContent,
            "topic", idea.getTitle(),
            "audience", idea.getTargetSegment()
        );
        
        Prompt prompt = extractTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                typeRef
            );
        } catch (Exception e) {
            log.error("Failed to extract image prompts", e);
            return List.of(
                "Professional illustration representing " + idea.getTitle(),
                "Modern graphic design for " + idea.getKeyMessage()
            );
        }
    }
    
    private String enhanceImagePrompt(String basePrompt, ImageRequirements requirements) {
        StringBuilder enhancedPrompt = new StringBuilder(basePrompt);
        
        // Add style requirements
        if (requirements.getStyle() != null) {
            enhancedPrompt.append(", ").append(requirements.getStyle()).append(" style");
        }
        
        // Add color scheme
        if (requirements.getColorScheme() != null) {
            enhancedPrompt.append(", ").append(requirements.getColorScheme()).append(" color scheme");
        }
        
        // Add quality and format specifications
        enhancedPrompt.append(", high quality, professional, detailed, 4k resolution");
        
        return enhancedPrompt.toString();
    }
    
    private String generateAltText(String imagePrompt) {
        PromptTemplate altTextTemplate = new PromptTemplate("""
            Generate concise, descriptive alt text for an image created with this prompt:
            "{imagePrompt}"
            
            The alt text should:
            - Be 125 characters or less
            - Describe what's visually in the image
            - Be helpful for screen readers
            - Not include "image of" or "picture of"
            
            Alt text:
            """);
        
        Map<String, Object> variables = Map.of("imagePrompt", imagePrompt);
        
        Prompt prompt = altTextTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent().trim();
    }
    
    private List<SocialMediaVariant> generateSocialMediaVariants(String textContent, 
                                                               ContentIdea idea,
                                                               List<SocialPlatform> platforms) {
        List<SocialMediaVariant> variants = new ArrayList<>();
        
        for (SocialPlatform platform : platforms) {
            try {
                String variant = generatePlatformSpecificContent(textContent, idea, platform);
                List<String> hashtags = generateHashtags(idea, platform);
                
                variants.add(SocialMediaVariant.builder()
                    .platform(platform)
                    .content(variant)
                    .hashtags(hashtags)
                    .characterCount(variant.length())
                    .estimatedReach(calculateEstimatedReach(platform, hashtags.size()))
                    .build());
                    
            } catch (Exception e) {
                log.error("Failed to generate content for platform: " + platform, e);
            }
        }
        
        return variants;
    }
    
    private String generatePlatformSpecificContent(String textContent, ContentIdea idea, 
                                                 SocialPlatform platform) {
        PromptTemplate platformTemplate = new PromptTemplate("""
            Adapt the following content for {platform}:
            
            Original Content: {textContent}
            Key Message: {keyMessage}
            Call to Action: {callToAction}
            
            Platform-specific requirements for {platform}:
            {platformRequirements}
            
            Create engaging content that:
            - Fits the platform's character limit and style
            - Uses platform-appropriate language and tone
            - Includes platform-specific features (mentions, hashtags, etc.)
            - Encourages engagement (likes, shares, comments)
            
            {platform} Post:
            """);
        
        Map<String, Object> requirements = getPlatformRequirements(platform);
        
        Map<String, Object> variables = Map.of(
            "platform", platform.getName(),
            "textContent", textContent.length() > 500 ? textContent.substring(0, 500) + "..." : textContent,
            "keyMessage", idea.getKeyMessage(),
            "callToAction", idea.getCallToAction(),
            "platformRequirements", objectMapper.writeValueAsString(requirements)
        );
        
        Prompt prompt = platformTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }
    
    private Map<String, Object> getPlatformRequirements(SocialPlatform platform) {
        switch (platform) {
            case TWITTER:
                return Map.of(
                    "characterLimit", 280,
                    "style", "concise and punchy",
                    "features", "hashtags, mentions, threads",
                    "engagement", "retweets, replies, likes"
                );
            case LINKEDIN:
                return Map.of(
                    "characterLimit", 3000,
                    "style", "professional and insightful",
                    "features", "industry hashtags, professional mentions",
                    "engagement", "thoughtful comments, shares"
                );
            case INSTAGRAM:
                return Map.of(
                    "characterLimit", 2200,
                    "style", "visual-first, storytelling",
                    "features", "hashtags (up to 30), stories, reels",
                    "engagement", "likes, comments, saves, shares"
                );
            case FACEBOOK:
                return Map.of(
                    "characterLimit", 63206,
                    "style", "community-focused, conversational",
                    "features", "hashtags, mentions, groups",
                    "engagement", "reactions, comments, shares"
                );
            default:
                return Map.of(
                    "characterLimit", 280,
                    "style", "general social media",
                    "features", "hashtags, mentions",
                    "engagement", "likes, comments, shares"
                );
        }
    }
    
    private List<String> generateHashtags(ContentIdea idea, SocialPlatform platform) {
        PromptTemplate hashtagTemplate = new PromptTemplate("""
            Generate relevant hashtags for the following content on {platform}:
            
            Topic: {topic}
            Key Message: {keyMessage}
            Keywords: {keywords}
            Target Audience: {audience}
            
            Requirements:
            - Generate 5-10 hashtags for {platform}
            - Mix of popular and niche hashtags
            - Include branded hashtags if applicable
            - Ensure hashtags are relevant and trending
            
            Return as JSON array: ["hashtag1", "hashtag2", ...]
            (without # symbol)
            """);
        
        Map<String, Object> variables = Map.of(
            "platform", platform.getName(),
            "topic", idea.getTitle(),
            "keyMessage", idea.getKeyMessage(),
            "keywords", String.join(", ", idea.getKeywords()),
            "audience", idea.getTargetSegment()
        );
        
        Prompt prompt = hashtagTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {};
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                typeRef
            );
        } catch (Exception e) {
            log.error("Failed to generate hashtags", e);
            return idea.getKeywords(); // Fallback to keywords
        }
    }
    
    private int calculateEstimatedReach(SocialPlatform platform, int hashtagCount) {
        // Simple heuristic for estimated reach based on platform and hashtag count
        int baseReach = switch (platform) {
            case INSTAGRAM -> 500;
            case TWITTER -> 300;
            case LINKEDIN -> 200;
            case FACEBOOK -> 150;
            default -> 100;
        };
        
        // Hashtags can increase reach
        int hashtagBonus = Math.min(hashtagCount * 50, 500);
        
        return baseReach + hashtagBonus;
    }
    
    private SEOMetadata generateSEOMetadata(String textContent, ContentIdea idea) {
        PromptTemplate seoTemplate = new PromptTemplate("""
            Generate SEO metadata for the following content:
            
            Content: {content}
            Title: {title}
            Keywords: {keywords}
            
            Generate:
            1. SEO title (50-60 characters)
            2. Meta description (150-160 characters)
            3. Additional keywords for targeting
            4. Suggested URL slug
            5. Schema markup suggestions
            
            Return as JSON:
            {
                "seo_title": "Optimized title",
                "meta_description": "Compelling description",
                "keywords": ["keyword1", "keyword2"],
                "url_slug": "seo-friendly-url",
                "schema_markup": "Article",
                "open_graph": {
                    "title": "Social media title",
                    "description": "Social description"
                }
            }
            """);
        
        Map<String, Object> variables = Map.of(
            "content", textContent.length() > 1000 ? textContent.substring(0, 1000) + "..." : textContent,
            "title", idea.getTitle(),
            "keywords", String.join(", ", idea.getKeywords())
        );
        
        Prompt prompt = seoTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                SEOMetadata.class
            );
        } catch (Exception e) {
            log.error("Failed to generate SEO metadata", e);
            return SEOMetadata.defaultMetadata(idea);
        }
    }
}
```

#### 3. A/B Testing Service

```java
@Service
public class ContentABTestingService {
    private final MultiModalContentGenerator contentGenerator;
    private final ContentPerformanceService performanceService;
    private final ABTestRepository testRepository;
    
    @Async
    public CompletableFuture<ABTestResult> createABTest(ABTestRequest request) {
        
        // Generate multiple content variations
        List<GeneratedContent> variations = generateContentVariations(request);
        
        // Create A/B test configuration
        ABTest test = ABTest.builder()
            .testName(request.getTestName())
            .hypothesis(request.getHypothesis())
            .variations(variations)
            .testDuration(request.getTestDuration())
            .successMetrics(request.getSuccessMetrics())
            .targetAudience(request.getTargetAudience())
            .trafficSplit(calculateTrafficSplit(variations.size()))
            .status(ABTestStatus.CREATED)
            .createdAt(LocalDateTime.now())
            .build();
        
        testRepository.save(test);
        
        // Start the test
        startABTest(test);
        
        return CompletableFuture.completedFuture(
            ABTestResult.builder()
                .testId(test.getId())
                .status(ABTestStatus.RUNNING)
                .variations(variations)
                .estimatedDuration(request.getTestDuration())
                .build()
        );
    }
    
    private List<GeneratedContent> generateContentVariations(ABTestRequest request) {
        List<GeneratedContent> variations = new ArrayList<>();
        ContentIdea baseIdea = request.getContentIdea();
        
        // Generate control version (original)
        ContentGenerationRequest controlRequest = ContentGenerationRequest.builder()
            .contentIdea(baseIdea)
            .format(request.getContentFormat())
            .targetAudience(request.getTargetAudience())
            .additionalRequirements(request.getBaseRequirements())
            .includeImages(true)
            .includeSocialVariants(true)
            .build();
        
        GeneratedContent control = contentGenerator.generateContent(controlRequest);
        control.setVariationName("Control");
        variations.add(control);
        
        // Generate test variations based on test parameters
        for (ABTestVariation variation : request.getTestVariations()) {
            ContentGenerationRequest variationRequest = createVariationRequest(
                controlRequest, variation
            );
            
            GeneratedContent generated = contentGenerator.generateContent(variationRequest);
            generated.setVariationName(variation.getName());
            generated.setVariationDescription(variation.getDescription());
            variations.add(generated);
        }
        
        return variations;
    }
    
    private ContentGenerationRequest createVariationRequest(ContentGenerationRequest baseRequest,
                                                          ABTestVariation variation) {
        ContentGenerationRequest.ContentGenerationRequestBuilder builder = 
            ContentGenerationRequest.builder()
                .contentIdea(baseRequest.getContentIdea())
                .format(baseRequest.getFormat())
                .targetAudience(baseRequest.getTargetAudience())
                .includeImages(baseRequest.isIncludeImages())
                .includeSocialVariants(baseRequest.isIncludeSocialVariants());
        
        // Apply variation-specific modifications
        Map<String, String> modifiedRequirements = new HashMap<>(baseRequest.getAdditionalRequirements());
        
        switch (variation.getType()) {
            case HEADLINE_TEST:
                modifiedRequirements.put("headline_style", variation.getParameters().get("style"));
                modifiedRequirements.put("headline_length", variation.getParameters().get("length"));
                break;
                
            case CTA_TEST:
                modifiedRequirements.put("cta_style", variation.getParameters().get("cta_style"));
                modifiedRequirements.put("cta_placement", variation.getParameters().get("placement"));
                break;
                
            case TONE_TEST:
                modifiedRequirements.put("tone", variation.getParameters().get("tone"));
                modifiedRequirements.put("formality_level", variation.getParameters().get("formality"));
                break;
                
            case LENGTH_TEST:
                modifiedRequirements.put("content_length", variation.getParameters().get("length"));
                modifiedRequirements.put("detail_level", variation.getParameters().get("detail_level"));
                break;
                
            case VISUAL_TEST:
                builder.imageRequirements(ImageRequirements.builder()
                    .style(variation.getParameters().get("image_style"))
                    .colorScheme(variation.getParameters().get("color_scheme"))
                    .build());
                break;
        }
        
        return builder.additionalRequirements(modifiedRequirements).build();
    }
    
    private Map<String, Double> calculateTrafficSplit(int variationCount) {
        Map<String, Double> split = new HashMap<>();
        double percentage = 1.0 / variationCount;
        
        split.put("Control", percentage);
        for (int i = 1; i < variationCount; i++) {
            split.put("Variation_" + i, percentage);
        }
        
        return split;
    }
    
    private void startABTest(ABTest test) {
        // Update test status
        test.setStatus(ABTestStatus.RUNNING);
        test.setStartedAt(LocalDateTime.now());
        testRepository.save(test);
        
        // Schedule test monitoring and analysis
        scheduleTestMonitoring(test);
    }
    
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void monitorRunningTests() {
        List<ABTest> runningTests = testRepository.findByStatus(ABTestStatus.RUNNING);
        
        for (ABTest test : runningTests) {
            try {
                ABTestAnalysis analysis = analyzeTestProgress(test);
                
                // Check if test should be stopped
                if (shouldStopTest(test, analysis)) {
                    stopTest(test, analysis);
                }
                
                // Update test metrics
                updateTestMetrics(test, analysis);
                
            } catch (Exception e) {
                log.error("Failed to monitor A/B test: " + test.getId(), e);
            }
        }
    }
    
    private ABTestAnalysis analyzeTestProgress(ABTest test) {
        // Collect performance data for all variations
        Map<String, VariationMetrics> variationMetrics = new HashMap<>();
        
        for (GeneratedContent variation : test.getVariations()) {
            ContentPerformanceMetrics metrics = performanceService.getMetrics(
                variation.getId(),
                test.getStartedAt(),
                LocalDateTime.now()
            );
            
            variationMetrics.put(variation.getVariationName(), 
                VariationMetrics.builder()
                    .views(metrics.getViews())
                    .clicks(metrics.getClicks())
                    .conversions(metrics.getConversions())
                    .engagementRate(metrics.getEngagementRate())
                    .conversionRate(metrics.getConversionRate())
                    .build()
            );
        }
        
        // Calculate statistical significance
        StatisticalSignificance significance = calculateStatisticalSignificance(variationMetrics);
        
        return ABTestAnalysis.builder()
            .testId(test.getId())
            .variationMetrics(variationMetrics)
            .statisticalSignificance(significance)
            .winningVariation(determineWinningVariation(variationMetrics))
            .confidence(significance.getConfidence())
            .analysisDate(LocalDateTime.now())
            .build();
    }
    
    private boolean shouldStopTest(ABTest test, ABTestAnalysis analysis) {
        // Stop if statistical significance reached
        if (analysis.getStatisticalSignificance().isSignificant() && 
            analysis.getConfidence() >= 0.95) {
            return true;
        }
        
        // Stop if test duration exceeded
        if (test.getStartedAt().plus(test.getTestDuration()).isBefore(LocalDateTime.now())) {
            return true;
        }
        
        // Stop if one variation is clearly performing poorly
        if (hasDefinitiveLoser(analysis)) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasDefinitiveLoser(ABTestAnalysis analysis) {
        VariationMetrics control = analysis.getVariationMetrics().get("Control");
        
        for (Map.Entry<String, VariationMetrics> entry : analysis.getVariationMetrics().entrySet()) {
            if (!"Control".equals(entry.getKey())) {
                VariationMetrics variation = entry.getValue();
                
                // If variation performs significantly worse (>50% worse conversion)
                if (variation.getConversionRate() < control.getConversionRate() * 0.5) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void stopTest(ABTest test, ABTestAnalysis analysis) {
        test.setStatus(ABTestStatus.COMPLETED);
        test.setCompletedAt(LocalDateTime.now());
        test.setFinalAnalysis(analysis);
        testRepository.save(test);
        
        // Generate test report
        generateTestReport(test, analysis);
        
        // Notify stakeholders
        notifyTestCompletion(test, analysis);
    }
    
    private void generateTestReport(ABTest test, ABTestAnalysis analysis) {
        PromptTemplate reportTemplate = new PromptTemplate("""
            Generate a comprehensive A/B test report based on the following data:
            
            Test Name: {testName}
            Hypothesis: {hypothesis}
            Test Duration: {duration}
            
            Results: {analysisData}
            
            Create a detailed report including:
            1. **Executive Summary** - Key findings and recommendations
            2. **Test Setup** - Hypothesis, variations, and methodology
            3. **Results Analysis** - Performance metrics for each variation
            4. **Statistical Significance** - Confidence levels and validity
            5. **Key Insights** - What we learned from the test
            6. **Recommendations** - Next steps and future testing ideas
            7. **Implementation Guide** - How to implement the winning variation
            
            Format the report in professional markdown format.
            """);
        
        Map<String, Object> variables = Map.of(
            "testName", test.getTestName(),
            "hypothesis", test.getHypothesis(),
            "duration", test.getTestDuration().toString(),
            "analysisData", objectMapper.writeValueAsString(analysis)
        );
        
        Prompt prompt = reportTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        String report = response.getResult().getOutput().getContent();
        
        // Save report
        ABTestReport testReport = ABTestReport.builder()
            .testId(test.getId())
            .report(report)
            .generatedAt(LocalDateTime.now())
            .build();
        
        testReportRepository.save(testReport);
    }
}
```

#### 4. Content Platform Controller

```java
@RestController
@RequestMapping("/api/v1/content")
@SecurityRequirement(name = "bearerAuth")
public class ContentPlatformController {
    private final ContentStrategyService strategyService;
    private final MultiModalContentGenerator contentGenerator;
    private final ContentABTestingService abTestingService;
    private final ContentPerformanceService performanceService;
    
    @PostMapping("/strategy")
    public ResponseEntity<ContentStrategy> generateStrategy(@RequestBody ContentStrategyRequest request) {
        try {
            ContentStrategy strategy = strategyService.generateContentStrategy(request);
            return ResponseEntity.ok(strategy);
        } catch (Exception e) {
            log.error("Content strategy generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/ideas")
    public ResponseEntity<List<ContentIdea>> generateIdeas(@RequestBody ContentIdeaRequest request) {
        try {
            List<ContentIdea> ideas = strategyService.generateContentIdeas(request);
            return ResponseEntity.ok(ideas);
        } catch (Exception e) {
            log.error("Content idea generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/generate")
    public ResponseEntity<GeneratedContent> generateContent(@RequestBody ContentGenerationRequest request) {
        try {
            // Validate request
            if (request.getContentIdea() == null) {
                return ResponseEntity.badRequest().build();
            }
            
            GeneratedContent content = contentGenerator.generateContent(request);
            return ResponseEntity.ok(content);
            
        } catch (Exception e) {
            log.error("Content generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/ab-test")
    public ResponseEntity<ABTestResult> createABTest(@RequestBody ABTestRequest request) {
        try {
            CompletableFuture<ABTestResult> testFuture = abTestingService.createABTest(request);
            ABTestResult result = testFuture.get(60, TimeUnit.SECONDS);
            return ResponseEntity.ok(result);
            
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
        } catch (Exception e) {
            log.error("A/B test creation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/performance/{contentId}")
    public ResponseEntity<ContentPerformanceReport> getPerformance(@PathVariable String contentId,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        try {
            ContentPerformanceReport report = performanceService.generateReport(contentId, from, to);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Performance report generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/ab-test/{testId}/results")
    public ResponseEntity<ABTestAnalysis> getABTestResults(@PathVariable String testId) {
        try {
            Optional<ABTest> testOpt = testRepository.findById(testId);
            if (testOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ABTest test = testOpt.get();
            if (test.getStatus() != ABTestStatus.COMPLETED) {
                return ResponseEntity.badRequest().build();
            }
            
            return ResponseEntity.ok(test.getFinalAnalysis());
        } catch (Exception e) {
            log.error("Failed to get A/B test results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/optimize")
    public ResponseEntity<ContentOptimizationResult> optimizeContent(@RequestBody ContentOptimizationRequest request) {
        try {
            ContentOptimizationResult result = optimizationService.optimizeContent(
                request.getContent(),
                request.getTargetAudience(),
                request.getGoals()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Content optimization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/trends/{industry}")
    public ResponseEntity<List<ContentTrend>> getContentTrends(@PathVariable String industry,
                                                              @RequestParam(defaultValue = "30") int days) {
        try {
            List<ContentTrend> trends = marketResearchService.getContentTrends(industry, days);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Failed to get content trends", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

---

## Implementation Best Practices

### 1. Error Handling and Resilience

```java
@Component
public class AIServiceErrorHandler {
    private final CircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;
    
    @EventListener
    public void handleChatModelException(ChatModelException e) {
        log.error("Chat model error: {}", e.getMessage());
        
        // Implement fallback logic
        if (e.getCause() instanceof RateLimitException) {
            // Implement exponential backoff
            scheduleRetry(e.getRequest(), calculateBackoffDelay(e));
        }
        
        // Update circuit breaker state
        circuitBreaker.recordException(e);
    }
    
    @Retryable(value = {TransientException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public <T> T executeWithRetry(Supplier<T> operation) {
        return retryTemplate.execute(context -> operation.get());
    }
}
```

### 2. Performance Monitoring

```java
@Component
public class AIPerformanceMonitor {
    private final MeterRegistry meterRegistry;
    private final Timer chatModelTimer;
    private final Counter requestCounter;
    
    @EventListener
    public void onChatModelRequest(ChatModelRequestEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(chatModelTimer);
        
        requestCounter.increment(
            Tags.of("model", event.getModelName(), "status", event.getStatus())
        );
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void reportMetrics() {
        // Report custom metrics
        Gauge.builder("ai.active_conversations")
            .register(meterRegistry, conversationManager::getActiveConversationCount);
    }
}
```

### 3. Security and Compliance

```java
@Service
public class ContentSecurityService {
    private final ContentFilterService contentFilter;
    private final PIIDetectionService piiDetection;
    
    public SecurityValidationResult validateContent(String content, ContentType type) {
        List<SecurityIssue> issues = new ArrayList<>();
        
        // Check for inappropriate content
        if (contentFilter.containsInappropriateContent(content)) {
            issues.add(SecurityIssue.INAPPROPRIATE_CONTENT);
        }
        
        // Check for PII
        List<PIIEntity> piiEntities = piiDetection.detectPII(content);
        if (!piiEntities.isEmpty()) {
            issues.add(SecurityIssue.PII_DETECTED);
        }
        
        // Check for prompt injection attempts
        if (detectPromptInjection(content)) {
            issues.add(SecurityIssue.PROMPT_INJECTION);
        }
        
        return SecurityValidationResult.builder()
            .isValid(issues.isEmpty())
            .issues(issues)
            .piiEntities(piiEntities)
            .build();
    }
    
    private boolean detectPromptInjection(String content) {
        String[] injectionPatterns = {
            "ignore previous instructions",
            "forget everything above",
            "act as if you are",
            "pretend to be",
            "system prompt:"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(injectionPatterns)
            .anyMatch(lowerContent::contains);
    }
}
```

---

## Summary and Next Steps

Phase 12 provides comprehensive implementations of four major real-world AI applications:

1. **Conversational AI Chatbot** - Advanced context management, intent recognition, and multi-turn conversations
2. **Document Analysis System** - Intelligent document processing, Q&A, and structured data extraction
3. **AI Code Assistant** - Code analysis, optimization, documentation generation, and test creation
4. **Content Generation Platform** - Multi-modal content creation, A/B testing, and performance optimization

### Key Learning Outcomes:
- Integration of multiple AI services in production environments
- Advanced error handling and resilience patterns
- Performance monitoring and optimization
- Security considerations and compliance
- Real-world deployment strategies

### Next Phase Preparation:
With Phase 12 complete, you now have hands-on experience with complex Spring AI applications. Consider these advanced topics:

- **Custom Model Integration** - Integrating proprietary or specialized AI models
- **Advanced RAG Patterns** - Multi-agent systems, tool use, and reasoning chains
- **Enterprise Architecture** - Microservices patterns, event-driven architectures
- **AI Governance** - Model versioning, audit trails, and compliance frameworks
- **Production Optimization** - Caching strategies, load balancing, and cost optimization

These implementations provide a solid foundation for building production-grade AI applications with Spring AI and Spring Boot.readValue(
                response.getResult().getOutput().getContent(), 
                IntentResult.class
            );
        } catch (Exception e) {
            log.error("Failed to parse intent recognition response", e);
            return IntentResult.builder()
                .intent("unknown")
                .confidence(0.0)
                .requiresClarification(true)
                .build();
        }
    }
}
```

#### 3. Conversation Flow Handler

```java
@Service
public class ConversationFlowHandler {
    private final Map<String, IntentHandler> intentHandlers;
    private final ConversationContextManager contextManager;
    private final IntentRecognitionService intentService;
    
    public ConversationFlowHandler(List<IntentHandler> handlers,
                                 ConversationContextManager contextManager,
                                 IntentRecognitionService intentService) {
        this.intentHandlers = handlers.stream()
            .collect(Collectors.toMap(
                handler -> handler.getSupportedIntent(),
                handler -> handler
            ));
        this.contextManager = contextManager;
        this.intentService = intentService;
    }
    
    @Async
    public CompletableFuture<ChatbotResponse> processMessage(ChatMessage message) {
        String conversationId = message.getConversationId();
        
        // Get conversation context
        ConversationContext context = contextManager.getContext(conversationId)
            .orElse(ConversationContext.builder()
                .conversationId(conversationId)
                .userId(message.getUserId())
                .startTime(LocalDateTime.now())
                .messages(new ArrayList<>())
                .build());
        
        // Add user message to context
        context.addMessage(message);
        
        // Recognize intent
        IntentResult intentResult = intentService.recognizeIntent(
            message.getContent(), context
        );
        
        // Handle intent
        IntentHandler handler = intentHandlers.get(intentResult.getIntent());
        if (handler == null) {
            handler = intentHandlers.get("default");
        }
        
        ChatbotResponse response = handler.handle(message, context, intentResult);
        
        // Add response to context
        context.addMessage(ChatMessage.builder()
            .content(response.getMessage())
            .sender("assistant")
            .timestamp(LocalDateTime.now())
            .build());
        
        // Save updated context
        contextManager.saveContext(conversationId, context);
        
        return CompletableFuture.completedFuture(response);
    }
}
```

#### 4. Specific Intent Handlers

```java
@Component
public class SupportRequestHandler implements IntentHandler {
    private final ChatModel chatModel;
    private final TicketService ticketService;
    private final KnowledgeBaseService knowledgeBase;
    
    @Override
    public String getSupportedIntent() {
        return "request_support";
    }
    
    @Override
    public ChatbotResponse handle(ChatMessage message, ConversationContext context, 
                                IntentResult intentResult) {
        
        // First, try to find solution in knowledge base
        List<KnowledgeArticle> relevantArticles = knowledgeBase.searchRelevant(
            message.getContent(), 5
        );
        
        if (!relevantArticles.isEmpty()) {
            String solution = generateSolutionFromKnowledge(message, relevantArticles);
            
            return ChatbotResponse.builder()
                .message(solution)
                .intent("request_support")
                .suggestions(List.of(
                    "Was this helpful?",
                    "I need to speak with a human agent",
                    "Show me more options"
                ))
                .build();
        }
        
        // If no solution found, create support ticket
        SupportTicket ticket = ticketService.createTicket(
            context.getUserId(),
            message.getContent(),
            intentResult.getEntities()
        );
        
        return ChatbotResponse.builder()
            .message("I've created a support ticket #" + ticket.getTicketNumber() + 
                    " for you. Our team will respond within 2 hours.")
            .intent("request_support")
            .metadata(Map.of("ticket_id", ticket.getId()))
            .suggestions(List.of("Check ticket status", "Anything else I can help with?"))
            .build();
    }
    
    private String generateSolutionFromKnowledge(ChatMessage message, 
                                               List<KnowledgeArticle> articles) {
        PromptTemplate template = new PromptTemplate("""
            Based on the user's support request and the following knowledge articles,
            provide a helpful response that directly addresses their issue.
            
            User request: {request}
            
            Knowledge articles:
            {articles}
            
            Provide a clear, step-by-step solution if possible. If the articles
            don't fully address the issue, acknowledge what you can help with
            and suggest next steps.
            """);
        
        Map<String, Object> variables = Map.of(
            "request", message.getContent(),
            "articles", articles.stream()
                .map(article -> article.getTitle() + ": " + article.getContent())
                .collect(Collectors.joining("\n\n"))
        );
        
        Prompt prompt = template.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }
}
```

#### 5. Chatbot REST Controller

```java
@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = "*")
public class ChatbotController {
    private final ConversationFlowHandler flowHandler;
    private final ConversationService conversationService;
    
    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody ChatMessageRequest request,
                                       HttpServletRequest httpRequest) {
        
        // Rate limiting
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.isAllowed(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded"));
        }
        
        // Validate and sanitize input
        String sanitizedMessage = inputSanitizer.sanitize(request.getMessage());
        if (sanitizedMessage.length() > 1000) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Message too long"));
        }
        
        ChatMessage message = ChatMessage.builder()
            .conversationId(request.getConversationId())
            .userId(request.getUserId())
            .content(sanitizedMessage)
            .timestamp(LocalDateTime.now())
            .sender("user")
            .build();
        
        try {
            CompletableFuture<ChatbotResponse> responseFuture = 
                flowHandler.processMessage(message);
            
            ChatbotResponse response = responseFuture.get(30, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(response);
            
        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(Map.of("error", "Response timeout"));
        } catch (Exception e) {
            log.error("Error processing chat message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    @GetMapping("/conversation/{conversationId}/history")
    public ResponseEntity<List<ChatMessage>> getConversationHistory(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        
        List<ChatMessage> history = conversationService.getHistory(conversationId, limit);
        return ResponseEntity.ok(history);
    }
    
    @PostMapping("/conversation/{conversationId}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable String conversationId,
                                          @RequestBody FeedbackRequest feedback) {
        conversationService.saveFeedback(conversationId, feedback);
        return ResponseEntity.ok(Map.of("message", "Feedback submitted"));
    }
}
```

### Streaming Chat Implementation

```java
@GetMapping(value = "/stream/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@PathVariable String conversationId) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    
    // Add to active connections
    conversationService.addActiveConnection(conversationId, emitter);
    
    emitter.onCompletion(() -> 
        conversationService.removeActiveConnection(conversationId, emitter));
    emitter.onTimeout(() -> 
        conversationService.removeActiveConnection(conversationId, emitter));
    emitter.onError((e) -> 
        conversationService.removeActiveConnection(conversationId, emitter));
    
    return emitter;
}

@Service
public class StreamingChatService {
    private final ChatModel chatModel;
    private final Map<String, Set<SseEmitter>> activeConnections = new ConcurrentHashMap<>();
    
    public void streamResponse(String conversationId, String prompt) {
        Set<SseEmitter> emitters = activeConnections.get(conversationId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        
        // Use streaming chat model
        ChatOptions options = ChatOptionsBuilder.builder()
            .withTemperature(0.7f)
            .withStream(true)
            .build();
        
        Prompt streamingPrompt = new Prompt(prompt, options);
        
        Flux<ChatResponse> responseStream = chatModel.stream(streamingPrompt);
        
        responseStream
            .doOnNext(response -> {
                String content = response.getResult().getOutput().getContent();
                sendToEmitters(emitters, "data", content);
            })
            .doOnComplete(() -> {
                sendToEmitters(emitters, "end", "");
            })
            .doOnError(error -> {
                sendToEmitters(emitters, "error", error.getMessage());
            })
            .subscribe();
    }
    
    private void sendToEmitters(Set<SseEmitter> emitters, String event, String data) {
        Iterator<SseEmitter> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            try {
                emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data));
            } catch (IOException e) {
                iterator.remove();
                emitter.completeWithError(e);
            }
        }
    }
}
```

---

## 12.2 Document Analysis System

### Intelligent Document Processing Architecture

#### 1. Document Ingestion Service

```java
@Service
public class DocumentIngestionService {
    private final DocumentLoader pdfLoader;
    private final DocumentLoader docxLoader;
    private final TextSplitter textSplitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final DocumentAnalysisService analysisService;
    
    @Async
    public CompletableFuture<DocumentProcessingResult> processDocument(
            MultipartFile file, DocumentProcessingRequest request) {
        
        try {
            // Step 1: Load document
            List<Document> documents = loadDocument(file);
            
            // Step 2: Extract metadata
            DocumentMetadata metadata = extractMetadata(file, documents);
            
            // Step 3: Perform initial analysis
            DocumentAnalysisResult analysis = analysisService.analyzeDocument(documents);
            
            // Step 4: Split into chunks
            List<Document> chunks = textSplitter.split(documents);
            
            // Step 5: Generate embeddings and store
            List<Document> enrichedChunks = enrichChunksWithMetadata(chunks, metadata, analysis);
            vectorStore.add(enrichedChunks);
            
            // Step 6: Generate summary and extract key information
            DocumentSummary summary = generateDocumentSummary(documents);
            List<ExtractedEntity> entities = extractEntities(documents);
            
            // Step 7: Classify document
            DocumentClassification classification = classifyDocument(documents, analysis);
            
            // Step 8: Save to database
            ProcessedDocument processedDoc = ProcessedDocument.builder()
                .originalFileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .metadata(metadata)
                .summary(summary)
                .entities(entities)
                .classification(classification)
                .processingDate(LocalDateTime.now())
                .status(ProcessingStatus.COMPLETED)
                .build();
            
            documentRepository.save(processedDoc);
            
            return CompletableFuture.completedFuture(
                DocumentProcessingResult.success(processedDoc)
            );
            
        } catch (Exception e) {
            log.error("Document processing failed", e);
            return CompletableFuture.completedFuture(
                DocumentProcessingResult.failed(e.getMessage())
            );
        }
    }
    
    private List<Document> loadDocument(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        DocumentLoader loader;
        
        switch (contentType) {
            case "application/pdf":
                loader = pdfLoader;
                break;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                loader = docxLoader;
                break;
            default:
                throw new UnsupportedDocumentTypeException("Unsupported file type: " + contentType);
        }
        
        Resource resource = new InputStreamResource(file.getInputStream());
        return loader.load(resource);
    }
}
```

#### 2. Advanced Document Analysis

```java
@Service
public class DocumentAnalysisService {
    private final ChatModel chatModel;
    private final ImageModel imageModel;
    
    public DocumentAnalysisResult analyzeDocument(List<Document> documents) {
        String fullContent = documents.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));
        
        // Comprehensive document analysis
        PromptTemplate analysisTemplate = new PromptTemplate("""
            Analyze the following document and provide detailed insights:
            
            Document Content:
            {content}
            
            Provide analysis in JSON format:
            {
                "document_type": "contract|invoice|report|email|legal_document|technical_document|other",
                "language": "detected_language",
                "sentiment": {
                    "overall": "positive|negative|neutral",
                    "confidence": 0.95
                },
                "key_topics": ["topic1", "topic2", "topic3"],
                "complexity_level": "basic|intermediate|advanced|expert",
                "estimated_reading_time_minutes": 15,
                "urgency_indicators": ["urgent", "asap", "deadline"],
                "action_items": [
                    {
                        "action": "description",
                        "priority": "high|medium|low",
                        "due_date": "extracted_date_or_null"
                    }
                ],
                "entities": {
                    "persons": ["John Doe", "Jane Smith"],
                    "organizations": ["Company ABC", "Department XYZ"],
                    "locations": ["New York", "California"],
                    "dates": ["2024-01-15", "next Monday"],
                    "amounts": ["$1,000", "50%"]
                },
                "summary": "Brief summary of the document",
                "confidence": 0.92
            }
            """);
        
        Map<String, Object> variables = Map.of("content", 
            fullContent.length() > 10000 ? fullContent.substring(0, 10000) + "..." : fullContent);
        
        Prompt prompt = analysisTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                DocumentAnalysisResult.class
            );
        } catch (Exception e) {
            log.error("Failed to parse document analysis", e);
            return DocumentAnalysisResult.defaultAnalysis();
        }
    }
    
    public List<ExtractedEntity> extractStructuredData(List<Document> documents, 
                                                     String entityType) {
        String content = documents.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));
        
        PromptTemplate extractionTemplate = new PromptTemplate("""
            Extract all {entityType} from the following document.
            Be precise and only extract information that is explicitly stated.
            
            Document:
            {content}
            
            Return as JSON array:
            [
                {
                    "value": "extracted_value",
                    "context": "surrounding_context",
                    "confidence": 0.95,
                    "position": "paragraph_number_or_section"
                }
            ]
            """);
        
        Map<String, Object> variables = Map.of(
            "entityType", entityType,
            "content", content
        );
        
        Prompt prompt = extractionTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            TypeReference<List<ExtractedEntity>> typeRef = new TypeReference<List<ExtractedEntity>>() {};
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                typeRef
            );
        } catch (Exception e) {
            log.error("Failed to extract entities of type: " + entityType, e);
            return Collections.emptyList();
        }
    }
}
```

#### 3. Document Search and Q&A Service

```java
@Service
public class DocumentQAService {
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final DocumentRepository documentRepository;
    
    public DocumentQAResponse answerQuestion(String question, 
                                           DocumentQARequest request) {
        
        // Step 1: Search for relevant document chunks
        SearchRequest searchRequest = SearchRequest.query(question)
            .withTopK(request.getMaxResults())
            .withSimilarityThreshold(request.getSimilarityThreshold());
        
        // Add filters if specified
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            searchRequest = searchRequest.withFilterExpression(
                Filter.Expression.in("document_id", request.getDocumentIds())
            );
        }
        
        if (request.getDocumentTypes() != null && !request.getDocumentTypes().isEmpty()) {
            searchRequest = searchRequest.withFilterExpression(
                Filter.Expression.in("document_type", request.getDocumentTypes())
            );
        }
        
        List<Document> relevantChunks = vectorStore.similaritySearch(searchRequest);
        
        if (relevantChunks.isEmpty()) {
            return DocumentQAResponse.builder()
                .answer("I couldn't find relevant information to answer your question.")
                .confidence(0.0)
                .sources(Collections.emptyList())
                .build();
        }
        
        // Step 2: Generate answer using RAG
        String answer = generateAnswerWithRAG(question, relevantChunks, request);
        
        // Step 3: Extract source references
        List<SourceReference> sources = extractSourceReferences(relevantChunks);
        
        // Step 4: Calculate confidence score
        double confidence = calculateConfidenceScore(question, answer, relevantChunks);
        
        return DocumentQAResponse.builder()
            .answer(answer)
            .confidence(confidence)
            .sources(sources)
            .relevantChunks(relevantChunks.size())
            .processingTime(System.currentTimeMillis() - startTime)
            .build();
    }
    
    private String generateAnswerWithRAG(String question, 
                                       List<Document> relevantChunks,
                                       DocumentQARequest request) {
        
        String context = relevantChunks.stream()
            .map(doc -> "Source: " + doc.getMetadata().get("source") + "\n" + doc.getContent())
            .collect(Collectors.joining("\n\n"));
        
        PromptTemplate ragTemplate = new PromptTemplate("""
            Answer the user's question based on the provided context documents.
            Be precise and cite specific sources when possible.
            If the context doesn't contain enough information, clearly state what's missing.
            
            Question: {question}
            
            Context Documents:
            {context}
            
            Instructions:
            - Provide a comprehensive answer based on the context
            - Include specific details and examples from the documents
            - If information is contradictory, mention the different perspectives
            - Cite sources using the format [Source: filename/section]
            - If the answer requires information not in the context, clearly state this limitation
            
            Answer:
            """);
        
        Map<String, Object> variables = Map.of(
            "question", question,
            "context", context
        );
        
        ChatOptions options = ChatOptionsBuilder.builder()
            .withTemperature(request.getTemperature())
            .withMaxTokens(request.getMaxTokens())
            .build();
        
        Prompt prompt = ragTemplate.create(variables, options);
        ChatResponse response = chatModel.call(prompt);
        
        return response.getResult().getOutput().getContent();
    }
    
    public DocumentSummaryResponse generateDocumentSummary(String documentId, 
                                                         SummaryRequest request) {
        
        Optional<ProcessedDocument> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            throw new DocumentNotFoundException("Document not found: " + documentId);
        }
        
        ProcessedDocument document = docOpt.get();
        
        // Get document chunks from vector store
        SearchRequest searchRequest = SearchRequest.query("*")
            .withFilterExpression(Filter.Expression.eq("document_id", documentId))
            .withTopK(100);
        
        List<Document> chunks = vectorStore.similaritySearch(searchRequest);
        String fullContent = chunks.stream()
            .sorted((a, b) -> {
                Integer chunkA = (Integer) a.getMetadata().get("chunk_index");
                Integer chunkB = (Integer) b.getMetadata().get("chunk_index");
                return chunkA.compareTo(chunkB);
            })
            .map(Document::getContent)
            .collect(Collectors.joining("\n"));
        
        PromptTemplate summaryTemplate = new PromptTemplate("""
            Generate a {summaryType} summary of the following document:
            
            Document Title: {title}
            Document Type: {documentType}
            
            Content:
            {content}
            
            Summary Requirements:
            - Length: {summaryLength} words approximately
            - Focus: {focusAreas}
            - Include key points, important details, and actionable items
            - Maintain the original tone and context
            
            Summary:
            """);
        
        Map<String, Object> variables = Map.of(
            "summaryType", request.getSummaryType(),
            "title", document.getOriginalFileName(),
            "documentType", document.getClassification().getDocumentType(),
            "content", fullContent,
            "summaryLength", request.getSummaryLength(),
            "focusAreas", String.join(", ", request.getFocusAreas())
        );
        
        Prompt prompt = summaryTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        String summary = response.getResult().getOutput().getContent();
        
        return DocumentSummaryResponse.builder()
            .documentId(documentId)
            .summary(summary)
            .summaryType(request.getSummaryType())
            .wordCount(summary.split("\\s+").length)
            .generatedAt(LocalDateTime.now())
            .build();
    }
}
```

---

## 12.3 AI-Powered Code Assistant

### Code Analysis and Enhancement System

#### 1. Code Analysis Service

```java
@Service
public class CodeAnalysisService {
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final VectorStore codebaseVectorStore;
    
    public CodeAnalysisResult analyzeCode(CodeAnalysisRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        String context = request.getContext();
        
        PromptTemplate analysisTemplate = new PromptTemplate("""
            Analyze the following {language} code and provide comprehensive insights:
            
            Context: {context}
            
            Code:
            ```{language}
            {code}
            ```
            
            Provide analysis in JSON format:
            {
                "code_quality": {
                    "overall_score": 85,
                    "readability": 90,
                    "maintainability": 80,
                    "performance": 85,
                    "security": 75
                },
                "issues": [
                    {
                        "type": "performance|security|bug|style|maintainability",
                        "severity": "critical|high|medium|low|info",
                        "line": 15,
                        "message": "Description of the issue",
                        "suggestion": "How to fix it"
                    }
                ],
                "improvements": [
                    {
                        "category": "performance|readability|security|structure",
                        "description": "Improvement suggestion",
                        "impact": "high|medium|low",
                        "effort": "high|medium|low"
                    }
                ],
                "patterns_detected": [
                    {
                        "pattern": "singleton|factory|observer|mvc",
                        "confidence": 0.95,
                        "location": "class MyClass"
                    }
                ],
                "complexity_metrics": {
                    "cyclomatic_complexity": 12,
                    "cognitive_complexity": 8,
                    "lines_of_code": 150,
                    "functions_count": 6
                },
                "dependencies": ["external_lib_1", "external_lib_2"],
                "test_suggestions": [
                    "Test edge case for null input",
                    "Test performance with large datasets"
                ]
            }
            """);
        
        Map<String, Object> variables = Map.of(
            "language", language,
            "context", context != null ? context : "No specific context provided",
            "code", code
        );
        
        Prompt prompt = analysisTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.readValue(
                response.getResult().getOutput().getContent(),
                CodeAnalysisResult.class
            );
        } catch (Exception e) {
            log.error("Failed to parse code analysis result", e);
            return CodeAnalysisResult.defaultResult();
        }
    }
    
    public CodeOptimizationResult optimizeCode(CodeOptimizationRequest request) {
        String code = request.getCode();
        String optimizationGoal = request.getOptimizationGoal(); // performance, readability, security
        
        PromptTemplate optimizationTemplate = new PromptTemplate("""
            Optimize the following {language} code for {goal}.
            
            Original Code:
            ```{language}
            {code}
            ```
            
            Requirements:
            - Maintain the same functionality
            - Focus on {goal} improvements
            - Provide explanations for each change
            - Ensure the code follows best practices
            
            Respond in JSON format:
            {
                "optimized_code": "the improved code",
                "changes": [
                    {
                        "type": "refactoring|performance|security|style",
                        "description": "What was changed",
                        "reason": "Why it was changed",
                        "impact": "Expected improvement"
                    }
                ],
                "metrics_improvement": {
                    "performance_gain": "estimated percentage",
                    "readability_score": "before -> after",
                    "complexity_reduction": "cyclomatic complexity change"
                }
            }
            """);
        
        Map<String, Object> variables = Map.of(
            "language", request.getLanguage(),
            "goal", optimizationGoal,
            "code", code
        );
        
        Prompt prompt = optimizationTemplate.create(variables);
        ChatResponse response = chatModel.call(prompt);
        
        try {
            return objectMapper.