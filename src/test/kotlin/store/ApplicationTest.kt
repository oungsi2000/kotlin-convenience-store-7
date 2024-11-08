package store

import camp.nextstep.edu.missionutils.test.Assertions.assertNowTest
import camp.nextstep.edu.missionutils.test.Assertions.assertSimpleTest
import camp.nextstep.edu.missionutils.test.NsTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

class ApplicationTest : NsTest() {
    @Test
    fun `파일에 있는 상품 목록 출력`() {
        assertSimpleTest {
            run("[물-1]", "N", "N")
            assertThat(output()).contains(
                "- 콜라 1,000원 10개 탄산2+1",
                "- 콜라 1,000원 10개",
                "- 사이다 1,000원 8개 탄산2+1",
                "- 사이다 1,000원 7개",
                "- 오렌지주스 1,800원 9개 MD추천상품",
                "- 오렌지주스 1,800원 재고 없음",
                "- 탄산수 1,200원 5개 탄산2+1",
                "- 탄산수 1,200원 재고 없음",
                "- 물 500원 10개",
                "- 비타민워터 1,500원 6개",
                "- 감자칩 1,500원 5개 반짝할인",
                "- 감자칩 1,500원 5개",
                "- 초코바 1,200원 5개 MD추천상품",
                "- 초코바 1,200원 5개",
                "- 에너지바 2,000원 5개",
                "- 정식도시락 6,400원 8개",
                "- 컵라면 1,700원 1개 MD추천상품",
                "- 컵라면 1,700원 10개"
            )
        }
    }

    @Test
    fun `여러 개의 일반 상품 구매`() {
        assertSimpleTest {
            run("[비타민워터-3],[물-2],[정식도시락-2]", "N", "N")
            assertThat(output().replace("\\s".toRegex(), "")).contains("내실돈18,300")
        }
    }

    @Test
    fun `기간에 해당하지 않는 프로모션 적용`() {
        assertNowTest({
            run("[감자칩-2]", "N", "N")
            assertThat(output().replace("\\s".toRegex(), "")).contains("내실돈3,000")
        }, LocalDate.of(2024, 2, 1).atStartOfDay())
    }

    @Test
    fun `예외 테스트`() {
        assertSimpleTest {
            runException("[컵라면-12]", "N", "N")
            assertThat(output()).contains("[ERROR] 재고 수량을 초과하여 구매할 수 없습니다. 다시 입력해 주세요.")
        }
    }

    override fun runMain() {
        main()
    }
}

class InventoryClassTest : NsTest() {

    @Test
    fun `재고 목록의 유효성을 검사한다`() {
        var inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "0001",
                ProductsColumn.QUANTITY to "0",
                ProductsColumn.PROMOTION to "null"),
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "dd"),
            )
        assertDoesNotThrow {
            InventoryManager(inventory)
        }
    }

    @ParameterizedTest
    @CsvSource(value = [
        "콜라:1000:-1:null",
        "콜라:-1:43:null",
        "콜라:0:0:프로모션"
                       ],
        delimiter = ':')
    fun `재고 목록이 타입과 일치하지 않을 시, IllegalArgumentException을 반환한다`(a:String, b:String, c:String, d:String) {
        var inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to a,
                ProductsColumn.PRICE to b,
                ProductsColumn.QUANTITY to c,
                ProductsColumn.PROMOTION to d),
        )
        assertThatThrownBy {
            InventoryManager(inventory)
        }
    }

    @Test
    fun `한 상품은 일반 상품 없이 프로모션 정보를 가질 수 없다`() {
        var inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "dd"),
        )
        assertThatThrownBy {
            InventoryManager(inventory)
        }
    }

    @Test
    fun `한 상품은 하나의 프로모션 정보를 가질 수 있다`() {
        var inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "dd"),
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "dd"),
        )
        assertThatThrownBy {
            InventoryManager(inventory)
        }
    }

    @Test
    fun `상품의 중복을 허용하지 않는다`() {
        var inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "null"),
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "12",
                ProductsColumn.PROMOTION to "null"),
        )
        assertThatThrownBy {
            InventoryManager(inventory)
        }

        inventory = mutableListOf(
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "프로모션"),
            mutableMapOf(
                ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "12",
                ProductsColumn.PROMOTION to "프로모션"),
        )
        assertThatThrownBy {
            InventoryManager(inventory)
        }
    }

    @Test
    fun `각 상품의 재고 수량을 고려하여 결제 가능 여부를 확인한다` () {
        var inventory = mutableListOf(
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "0",
                ProductsColumn.PROMOTION to "null"),
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "dd"),
            )
        var inventoryManager = InventoryManager(inventory)
        var product = "콜라"
        var isBuyable:Boolean = inventoryManager.checkProductAvailable(product)
        assertThat(isBuyable).isFalse()
    }

    @Test
    fun `고객이 상품을 구매할 때마다, 결제된 수량만큼 해당 상품의 재고에서 차감하여 수량을 관리한다`() {
        var inventory = mutableListOf(
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "탄산2+1"),
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "0",
                ProductsColumn.PROMOTION to "null"),
        )
        var inventoryManager = InventoryManager(inventory)
        var boughtProduct = "콜라"
        var boughtCount = 2

        var result = inventoryManager.deduct(boughtProduct, boughtCount, isPromotion = false)
        assertThat(inventoryManager.inventory[0][ProductsColumn.QUANTITY]).isEqualTo("10")
        assertThat(result).isEqualTo(InventoryManager.Msg.OUT_OF_STOCK)
    }

    @Test
    fun `재고를 차감함으로써 시스템은 최신 재고 상태를 유지하며, 다음 고객이 구매할 때 정확한 재고 정보를 제공한다`() {
        //java.io.File 로 파일을 덮어씌웁니다
        var inventory = mutableListOf(
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "탄산2+1"),
            mutableMapOf(ProductsColumn.NAME to "콜라",
                ProductsColumn.PRICE to "1000",
                ProductsColumn.QUANTITY to "10",
                ProductsColumn.PROMOTION to "null"),
        )
        var inventoryManager = InventoryManager(inventory)
        var CSVText = inventoryManager.inventoryToCSV()
        assertThat(CSVText).contains("""
            name,price,quantity,promotion
            콜라,1000,10,탄산2+1
            콜라,1000,10,null
        """.trimIndent())

    }

    override fun runMain() {
        main()
    }
}

class InputViewTest : NsTest() {

    override fun runMain() {
        main()
    }
}