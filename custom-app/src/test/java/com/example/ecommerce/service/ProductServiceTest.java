package com.example.ecommerce.service;

import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setName("Wireless Headphones");
        sampleProduct.setSku("WH-1000");
        sampleProduct.setPrice(new BigDecimal("79.99"));
        sampleProduct.setCategory("electronics");
    }

    @Test
    void getProduct_found() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        Product result = productService.getProduct(1L);

        assertThat(result.getName()).isEqualTo("Wireless Headphones");
        assertThat(result.getSku()).isEqualTo("WH-1000");
    }

    @Test
    void getProduct_notFound_throws() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void getProductBySku_found() {
        when(productRepository.findBySku("WH-1000")).thenReturn(Optional.of(sampleProduct));

        Product result = productService.getProductBySku("WH-1000");

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void searchProducts_returnsByCategory() {
        Product p1 = new Product();
        p1.setId(1L);
        p1.setCategory("electronics");
        Product p2 = new Product();
        p2.setId(2L);
        p2.setCategory("electronics");

        when(productRepository.findByCategory("electronics")).thenReturn(List.of(p1, p2));

        List<Product> results = productService.searchProducts("electronics");

        assertThat(results).hasSize(2);
    }

    @Test
    void getAllProducts_returnsAll() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        List<Product> results = productService.getAllProducts();

        assertThat(results).hasSize(1);
    }
}
