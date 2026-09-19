package com.eshoppingzone.product.controller;

import com.eshoppingzone.product.dto.ProductDto;
import com.eshoppingzone.product.entity.ProductStatus;
import com.eshoppingzone.product.exception.GlobalExceptionHandler;
import com.eshoppingzone.product.messaging.ProductViewEventPublisher;
import com.eshoppingzone.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.web.servlet.MockMvc;

import com.eshoppingzone.product.security.SecurityConfig;
import com.eshoppingzone.product.security.JwtAuthenticationFilter;
import com.eshoppingzone.product.security.JwtTokenProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, GlobalExceptionHandler.class})
class ProductSearchAndFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductViewEventPublisher productViewEventPublisher;

    private ProductDto productLaptop;
    private ProductDto productPhone;

    @BeforeEach
    void setUp() {
        productLaptop = new ProductDto(1L, 10L, "Gaming Laptop", "High performance laptop", "Electronics",
                new BigDecimal("1299.99"), "http://img.url/laptop.jpg", ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());

        productPhone = new ProductDto(2L, 10L, "Smartphone Pro", "Latest smartphone with OLED", "Electronics",
                new BigDecimal("799.99"), "http://img.url/phone.jpg", ProductStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ==========================================
    // SEARCH TESTS (1 - 4)
    // ==========================================

    @Test
    @DisplayName("1. Keyword search filters products by keyword in name or description")
    void test1_KeywordSearch() throws Exception {
        when(productService.getActiveProducts(eq(null), eq("Laptop"), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products").param("keyword", "Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("2. Case-insensitive keyword search works")
    void test2_CaseInsensitiveKeywordSearch() throws Exception {
        when(productService.getActiveProducts(eq(null), eq("laptop"), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products").param("keyword", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"));
    }

    @Test
    @DisplayName("3. No keyword returns active products without keyword filter")
    void test3_NoKeyword() throws Exception {
        when(productService.getActiveProducts(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop, productPhone), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("4. Keyword with no matching results returns empty page")
    void test4_KeywordWithNoResults() throws Exception {
        when(productService.getActiveProducts(eq(null), eq("NonExistent"), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/products").param("keyword", "NonExistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    // ==========================================
    // FILTERING TESTS (5 - 10)
    // ==========================================

    @Test
    @DisplayName("5. Category filter returns products matching category")
    void test5_CategoryFilter() throws Exception {
        when(productService.getActiveProducts(eq("Electronics"), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop, productPhone), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/products").param("category", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("6. Minimum price filter passes minPrice to service")
    void test6_MinPriceFilter() throws Exception {
        when(productService.getActiveProducts(eq(null), eq(null), eq(new BigDecimal("1000")), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products").param("minPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].price").value(1299.99));
    }

    @Test
    @DisplayName("7. Maximum price filter passes maxPrice to service")
    void test7_MaxPriceFilter() throws Exception {
        when(productService.getActiveProducts(eq(null), eq(null), eq(null), eq(new BigDecimal("800")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productPhone), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products").param("maxPrice", "800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].price").value(799.99));
    }

    @Test
    @DisplayName("8. Min + Max price range filter")
    void test8_MinAndMaxPriceFilter() throws Exception {
        when(productService.getActiveProducts(eq(null), eq(null), eq(new BigDecimal("700")), eq(new BigDecimal("900")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productPhone), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "700")
                        .param("maxPrice", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Smartphone Pro"));
    }

    @Test
    @DisplayName("9. Category + Price filter combined")
    void test9_CategoryAndPriceFilter() throws Exception {
        when(productService.getActiveProducts(eq("Electronics"), eq(null), eq(new BigDecimal("1000")), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "Electronics")
                        .param("minPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"));
    }

    @Test
    @DisplayName("10. Keyword + Category + Price combined filter")
    void test10_KeywordCategoryPriceFilter() throws Exception {
        when(productService.getActiveProducts(eq("Electronics"), eq("Gaming"), eq(new BigDecimal("1000")), eq(new BigDecimal("2000")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("keyword", "Gaming")
                        .param("category", "Electronics")
                        .param("minPrice", "1000")
                        .param("maxPrice", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"));
    }

    // ==========================================
    // SORTING TESTS (11 - 15)
    // ==========================================

    @Test
    @DisplayName("11. Sort by price ascending")
    void test11_SortByPriceAscending() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productPhone, productLaptop), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "price")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk());

        Pageable captured = pageableCaptor.getValue();
        Sort.Order priceOrder = captured.getSort().getOrderFor("price");
        assertNotNull(priceOrder);
        assertEquals(Sort.Direction.ASC, priceOrder.getDirection());
    }

    @Test
    @DisplayName("12. Sort by price descending")
    void test12_SortByPriceDescending() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop, productPhone), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "price")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk());

        Pageable captured = pageableCaptor.getValue();
        Sort.Order priceOrder = captured.getSort().getOrderFor("price");
        assertNotNull(priceOrder);
        assertEquals(Sort.Direction.DESC, priceOrder.getDirection());
    }

    @Test
    @DisplayName("13. Sort by name ascending (valid alternate field)")
    void test13_SortByNameAscending() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop, productPhone), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "name")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk());

        Pageable captured = pageableCaptor.getValue();
        Sort.Order nameOrder = captured.getSort().getOrderFor("name");
        assertNotNull(nameOrder);
        assertEquals(Sort.Direction.ASC, nameOrder.getDirection());
    }

    @Test
    @DisplayName("14. Invalid sort field (e.g. rating or stock) is rejected with 400 Bad Request")
    void test14_InvalidSortFieldRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sortBy", "rating"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid sort field")));

        mockMvc.perform(get("/api/v1/products").param("sortBy", "stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/products").param("sortBy", "unknownColumn"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("15. Invalid sort direction is rejected with 400 Bad Request")
    void test15_InvalidSortDirectionRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "price")
                        .param("sortDirection", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid sort direction")));
    }

    // ==========================================
    // PAGINATION TESTS (16 - 24)
    // ==========================================

    @Test
    @DisplayName("16. Default pagination uses page 0 and size 10")
    void test16_DefaultPagination() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber());
        assertEquals(10, captured.getPageSize());
    }

    @Test
    @DisplayName("17. Explicit page 0 request")
    void test17_Page0() throws Exception {
        when(productService.getActiveProducts(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.first").value(true));
    }

    @Test
    @DisplayName("18. Page 1 request")
    void test18_Page1() throws Exception {
        when(productService.getActiveProducts(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productPhone), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/v1/products").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("19. Page beyond available pages returns empty content with correct page number")
    void test19_PageBeyondAvailablePages() throws Exception {
        when(productService.getActiveProducts(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(99, 10), 5));

        mockMvc.perform(get("/api/v1/products").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(99))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    @Test
    @DisplayName("20. size = 1 accepted")
    void test20_Size1() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 1), 5));

        mockMvc.perform(get("/api/v1/products").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1));

        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("21. size = 50 (maximum allowed) accepted")
    void test21_Size50() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/v1/products").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));

        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("22. size > 50 rejected with 400 Bad Request")
    void test22_SizeGreaterThan50Rejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not exceed 50")));
    }

    @Test
    @DisplayName("23. size = 0 rejected with 400 Bad Request")
    void test23_Size0Rejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("greater than zero")));
    }

    @Test
    @DisplayName("24. Negative page rejected with 400 Bad Request")
    void test24_NegativePageRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must not be negative")));
    }

    // ==========================================
    // COMBINED TESTS (25)
    // ==========================================

    @Test
    @DisplayName("25. Combined Search + Filter + Sort + Pagination")
    void test25_CombinedSearchFilterSortPagination() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(eq("Electronics"), eq("Laptop"), eq(new BigDecimal("500")), eq(new BigDecimal("1500")), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("keyword", "Laptop")
                        .param("category", "Electronics")
                        .param("minPrice", "500")
                        .param("maxPrice", "1500")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sortBy", "price")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        Pageable captured = pageableCaptor.getValue();
        assertEquals(0, captured.getPageNumber());
        assertEquals(5, captured.getPageSize());
        assertEquals(Sort.Direction.ASC, captured.getSort().getOrderFor("price").getDirection());
        // Deterministic secondary sort
        assertNotNull(captured.getSort().getOrderFor("id"));
    }

    // ==========================================
    // EMPTY RESULT TESTS (26)
    // ==========================================

    @Test
    @DisplayName("26. Valid request with no matching products returns 200 OK with empty content")
    void test26_EmptyResultHandling() throws Exception {
        when(productService.getActiveProducts(eq("Books"), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/products").param("category", "Books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.empty").value(true));
    }

    // ==========================================
    // ADDITIONAL DETERMINISTIC SORT & ALIAS TESTS
    // ==========================================

    @Test
    @DisplayName("Deterministic pagination adds secondary sort on id when sortBy is not id")
    void testDeterministicPaginationSecondarySort() throws Exception {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productService.getActiveProducts(any(), any(), any(), any(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "name")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk());

        Pageable captured = pageableCaptor.getValue();
        Sort.Order primary = captured.getSort().getOrderFor("name");
        Sort.Order secondary = captured.getSort().getOrderFor("id");
        assertNotNull(primary);
        assertEquals(Sort.Direction.DESC, primary.getDirection());
        assertNotNull(secondary);
        assertEquals(Sort.Direction.DESC, secondary.getDirection());
    }

    @Test
    @DisplayName("/search alias endpoint works identically to /products")
    void testSearchAliasEndpoint() throws Exception {
        when(productService.getActiveProducts(eq(null), eq("Laptop"), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productLaptop), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products/search").param("keyword", "Laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Gaming Laptop"));
    }

    @Test
    @DisplayName("Invalid price range (minPrice > maxPrice) returns 400 Bad Request")
    void testInvalidPriceRangeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "1000")
                        .param("maxPrice", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot exceed maximum price")));
    }
}
