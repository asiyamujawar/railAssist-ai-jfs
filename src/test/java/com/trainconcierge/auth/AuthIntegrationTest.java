package com.trainconcierge.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.auth.dto.LoginRequest;
import com.trainconcierge.auth.dto.RegisterRequest;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import com.trainconcierge.user.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String LOGIN_URL = "/api/auth/login";
    private static final String ME_URL = "/api/auth/me";
    private static final String HEALTH_URL = "/api/health";

    private static final String VALID_EMAIL = "test.user@example.com";
    private static final String STRONG_PASSWORD = "Test@123";
    private static final String WEAK_PASSWORD = "123";
    private static final String INVALID_EMAIL = "not-an-email";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. REGISTRATION TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register — 201 Created with valid data")
    void register_Success_WithValidData() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Test")
                .lastName("User")
                .email(VALID_EMAIL)
                .password(STRONG_PASSWORD)
                .phoneNumber("+1234567890")
                .build();

        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully."))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(VALID_EMAIL))
                .andExpect(jsonPath("$.data.firstName").value("Test"))
                .andExpect(jsonPath("$.data.lastName").value("User"))
                .andExpect(jsonPath("$.data.role").value(UserRole.ROLE_USER.name()))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> data = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> authData = (Map<String, Object>) data.get("data");
        String token = (String) authData.get("accessToken");

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register — 409 Conflict with duplicate email")
    void register_Failure_DuplicateEmail() throws Exception {
        User existingUser = User.builder()
                .firstName("Existing")
                .lastName("User")
                .email(VALID_EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = RegisterRequest.builder()
                .firstName("Another")
                .lastName("User")
                .email(VALID_EMAIL)
                .password(STRONG_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_ALREADY_EXISTS.name()))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.path").value(REGISTER_URL));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/register — 400 Bad Request with validation errors")
    void register_Failure_ValidationErrors() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("")
                .lastName("")
                .email(INVALID_EMAIL)
                .password(WEAK_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.firstName").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/register — 400 Bad Request with missing body")
    void register_Failure_MissingBody() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_REQUEST_BODY.name()));
    }

    // ─────────────────────────────────────────────────────────────
    // 2. LOGIN TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/login — 200 OK with valid credentials")
    void login_Success_WithValidCredentials() throws Exception {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(VALID_EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build();
        userRepository.save(user);

        LoginRequest request = LoginRequest.builder()
                .email(VALID_EMAIL)
                .password(STRONG_PASSWORD)
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful."))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/auth/login — 401 Unauthorized with wrong password")
    void login_Failure_WrongPassword() throws Exception {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(VALID_EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build();
        userRepository.save(user);

        LoginRequest request = LoginRequest.builder()
                .email(VALID_EMAIL)
                .password("WrongPassword@1")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.AUTH_TOKEN_INVALID.name()))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("invalid")))
                .andExpect(jsonPath("$.path").value(LOGIN_URL));
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/auth/login — 401 Unauthorized with non-existent user")
    void login_Failure_NonExistentUser() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nobody@example.com")
                .password(STRONG_PASSWORD)
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(LOGIN_URL));
    }

    @Test
    @Order(8)
    @DisplayName("POST /api/auth/login — 400 Bad Request with validation errors")
    void login_Failure_ValidationErrors() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("")
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // 3. PROTECTED ENDPOINT TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("GET /api/auth/me — 200 OK with valid token")
    void me_Success_WithValidToken() throws Exception {
        User user = User.builder()
                .firstName("Profile")
                .lastName("Tester")
                .email(VALID_EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .phoneNumber("+1122334455")
                .build();
        userRepository.save(user);

        String token = generateTokenForUser(VALID_EMAIL);

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.firstName").value("Profile"))
                .andExpect(jsonPath("$.data.lastName").value("Tester"))
                .andExpect(jsonPath("$.data.email").value(VALID_EMAIL))
                .andExpect(jsonPath("$.data.phoneNumber").value("+1122334455"))
                .andExpect(jsonPath("$.data.role").value(UserRole.ROLE_USER.name()))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/auth/me — 401 Unauthorized with no token")
    void me_Failure_NoToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/auth/me — 401 Unauthorized with malformed token")
    void me_Failure_MalformedToken() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer not.a.valid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/auth/me — 401 Unauthorized with expired token")
    void me_Failure_ExpiredToken() throws Exception {
        User user = User.builder()
                .firstName("Expired")
                .lastName("Test")
                .email(VALID_EMAIL)
                .passwordHash(passwordEncoder.encode(STRONG_PASSWORD))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build();
        userRepository.save(user);

        String expiredToken = generateExpiredToken(VALID_EMAIL);

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.AUTH_TOKEN_EXPIRED.name()));
    }

    @Test
    @Order(13)
    @DisplayName("Protected endpoint — 401 Unauthorized with empty Bearer prefix")
    void protectedEndpoint_Failure_EmptyBearer() throws Exception {
        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(14)
    @DisplayName("Protected endpoint — 401 Unauthorized with wrong scheme")
    void protectedEndpoint_Failure_WrongAuthScheme() throws Exception {
        String token = generateTokenForUser(VALID_EMAIL);

        mockMvc.perform(get(ME_URL)
                        .header("Authorization", "Basic " + token))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────
    // 4. PUBLIC ENDPOINT TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("GET /api/health — 200 OK without token (public endpoint)")
    void health_Success_WithoutToken() throws Exception {
        mockMvc.perform(get(HEALTH_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/auth/register — case-insensitive email storage")
    void register_Success_CaseInsensitiveEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Case")
                .lastName("Insensitive")
                .email("MixedCase@Example.COM")
                .password(STRONG_PASSWORD)
                .build();

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("mixedcase@example.com"));

        LoginRequest loginRequest = LoginRequest.builder()
                .email("MIXEDCASE@EXAMPLE.COM")
                .password(STRONG_PASSWORD)
                .build();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────

    private String generateTokenForUser(String email) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(String email) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date pastExpiry = new Date(now.getTime() - 10000);

        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(now.getTime() - 3600000))
                .expiration(pastExpiry)
                .signWith(key)
                .compact();
    }
}
