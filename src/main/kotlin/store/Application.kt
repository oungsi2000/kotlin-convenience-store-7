package store

import store.InventoryManager.Msg
import java.nio.file.Paths
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ProductsColumn {
	NAME,
	PRICE,
	QUANTITY,
	PROMOTION
}

enum class PromotionsColumn {
	NAME, BUY, GET, START_DATE, END_DATE
}

class InventoryManager(var inventory:MutableList<MutableMap<ProductsColumn, String>> = mutableListOf(mutableMapOf())) {
	init {
		checkInventoryType()
		checkInventoryDuplex()
	}

	private fun checkInventoryType() {
		try {
			inventory.map {
				it[ProductsColumn.NAME]!!.toString()
				2.toUInt() / it[ProductsColumn.PRICE]?.toUInt()!! // 가격은 0이 될 수 없습니다
				it[ProductsColumn.QUANTITY]?.toUInt()
				it[ProductsColumn.PROMOTION]!!.toString()
			}
		} catch (exception: Exception) {
			throw IllegalArgumentException(Msg.ILLEGAL_TYPE.msg)
		}
	}

	private fun checkInventoryDuplex() {
		var productNamesNotDuplex: MutableSet<String?> = mutableSetOf()

		inventory.map { productNamesNotDuplex.add(it[ProductsColumn.NAME]) }

		for (key in productNamesNotDuplex) {
			var matches = inventory.filter { it[ProductsColumn.NAME] == key }
			var normalProduct = matches.filter { it[ProductsColumn.PROMOTION]?.trim() == "null" }
			var promotionProduct = matches.filter { it[ProductsColumn.PROMOTION]?.trim() != "null" }

			if (normalProduct.size > 1) { throw IllegalArgumentException(Msg.DUPLEX_NAME.msg) }
			if (promotionProduct.size > 1) { throw IllegalArgumentException(Msg.MULTIPLE_PROMOTION.msg) }
			if (normalProduct.isEmpty()) { throw IllegalArgumentException(Msg.NO_NORMAL_PRODUCT.msg) }
		}
	}

	fun checkProductAvailable(targetProduct:String):Boolean {
		var isNormalProductAvailable = false

		for (productInfo in inventory) {
			var productName = productInfo[ProductsColumn.NAME]
			var productStock = productInfo[ProductsColumn.QUANTITY]

			if (productName == targetProduct && productInfo[ProductsColumn.PROMOTION] == "null") {
				isNormalProductAvailable = productStock?.toInt()!! > 0
			}
		}
		return isNormalProductAvailable
	}


	fun deduct(boughtProduct:String, boughtAmount:Int, isPromotion:Boolean=false):Msg {
		var productInfo = inventory.withIndex().filter {
			it.value[ProductsColumn.NAME] == boughtProduct && it.value[ProductsColumn.PROMOTION] == "null"
		}

		if (isPromotion) {
			productInfo = inventory.withIndex().filter {
				it.value[ProductsColumn.NAME] == boughtProduct && it.value[ProductsColumn.PROMOTION] != "null"
			}
		}

		if (productInfo.isEmpty()) {
			return Msg.PRODUCT_NOT_FOUND
		}

		var index = productInfo[0].index
		var productStock = productInfo[0].value[ProductsColumn.QUANTITY]?.toInt()!!
		var currentStock = productStock - boughtAmount

		if (currentStock < 0 ) {
			return Msg.OUT_OF_STOCK
		}

		inventory[index][ProductsColumn.QUANTITY] = currentStock.toString()

		return Msg.SUCCESS
	}

	 fun inventoryToCSV():String {
		var inventoryCSV = "name,price,quantity,promotion\n"
		for (productInfo in inventory) {
			var text = productInfo.values.toList().joinToString(",") + "\n"
			inventoryCSV += text
		}
		return inventoryCSV
	}

	//파일 입출력을 이용하여 inventory 변수의 내용을 덮어씌웁니다
	fun dumpInventory() {
		Files.write(Paths.get(PRODUCTS_DIR), inventoryToCSV().toByteArray())
	}

	//상품 이름으로 재고를 검색하여 재고 정보를 반환합니다
	fun searchNormalProduct(productName:String):Map<ProductsColumn, String> {
		var searchResult = inventory.filter { it[ProductsColumn.NAME] == productName.trim() && it[ProductsColumn.PROMOTION] == "null"}
		return searchResult[0]
	}

	fun searchPromotionProduct(productName:String):Map<ProductsColumn, String> {
		var searchResult = inventory.filter { it[ProductsColumn.NAME] == productName.trim() && it[ProductsColumn.PROMOTION] != "null"}
		return searchResult[0]
	}

	companion object {
		val PRODUCTS_DIR = "src/main/resources/products.md"
		val PROMOTIONS_DIR = "src/main/resources/promotions.md"
	}

	enum class Msg (val msg:String, val isSuccess: Boolean) {
		PRODUCT_NOT_FOUND("상품을 찾을 수 없습니다", false),
		OUT_OF_STOCK("재고가 부족합니다", false),
		SUCCESS("재고 변경에 성공하였습니다", true),
		ILLEGAL_TYPE("재고 타입이 올바른 타입이 아닙니다", false),
		DUPLEX_NAME("중복된 상품이 존재합니다", false),
		MULTIPLE_PROMOTION("한 상품에 두 가지 이상의 프로모션이 존재합니다", false),
		NO_NORMAL_PRODUCT("일반 상품 없이 프로모션 정보를 가질 수 없습니다. 일반 상품 재고가 없다면 꼭 0을 입력해주세요", false)

	}
}

class PromotionManager (var promotion:MutableList<MutableMap<PromotionsColumn, String>>,
												var inventoryManager: InventoryManager = InventoryManager()
) {

	init {
		checkPromotionValid()
		checkPromotionDuplex()
	}

	private fun checkPromotionValid() {
		try {
			promotion.map {
				it[PromotionsColumn.BUY]!!.toString()
				2.toUInt() / it[PromotionsColumn.BUY]?.toUInt()!! //0이 될 수 없습니다
				2.toUInt() / it[PromotionsColumn.GET]?.toUInt()!! //0이 될 수 없습니다

				val startDate = stringToLocalDate(it[PromotionsColumn.START_DATE]!!)
				val endDate = stringToLocalDate(it[PromotionsColumn.END_DATE]!!)

				//end_date가 start_date보다 빠를 수 없다
				if (startDate > endDate) {
					throw IllegalArgumentException(Msg.ILLEGAL_TIME.msg)
				}
			}
		} catch (exception: Exception) {
			throw IllegalArgumentException(Msg.ILLEGAL_TYPE.msg)
		}
	}

	private fun checkPromotionDuplex () {
		var promotionNamesNotDuplex: MutableSet<String?> = mutableSetOf()

		promotion.map { promotionNamesNotDuplex.add(it[PromotionsColumn.NAME]) }

		for (key in promotionNamesNotDuplex) {
			var matches = promotion.filter { it[PromotionsColumn.NAME] == key }
			var periods = matches.map {
				var startDate = stringToLocalDate(it[PromotionsColumn.START_DATE]!!)
				var endDate = stringToLocalDate(it[PromotionsColumn.END_DATE]!!)
				listOf(startDate, endDate)
			}
			if (getIsOverLapped(periods)) {
				throw IllegalArgumentException(Msg.PERIOD_OVERLAPPED.msg)
			}
		}
	}

	private fun getIsOverLapped(periods:List<List<LocalDate>>):Boolean{
		var overlapped:MutableList<Boolean> = mutableListOf()
		//가능한 모든 순서쌍
		for (i in 0..<periods.size) {
			for (j in i+1..<periods.size) {
				overlapped.add(isOverlapped(periods[i], periods[j]))
			}
		}
		return overlapped.any { it }
	}
	private fun isOverlapped(period1:List<LocalDate>, period2:List<LocalDate>):Boolean {
		return (period1[0] >= period2[0] && period1[0] <= period2[1]) ||
						(period1[1] >= period2[1] && period1[1] <= period2[1]) ||
						(period2[0] >= period1[0] && period2[0] <= period1[1]) ||
						(period2[1] >= period1[0] && period2[1] <= period1[1])
	}


	fun stringToLocalDate (dateString:String):LocalDate {
		val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
		return LocalDate.parse(dateString, formatter)
	}

	fun getValidPromotions ():List<MutableMap<PromotionsColumn, String>> {
		return promotion.filter {
			var startDate = stringToLocalDate(it[PromotionsColumn.START_DATE]!!)
			var endDate = stringToLocalDate(it[PromotionsColumn.END_DATE]!!)
			LocalDate.now() in startDate..endDate
		}
	}

	private fun assignBuyAmount (buyAmount:Int, promotionBuy:Int, promotionGet:Int, productStock:Int):List<Int> {
		var normalProductBuyAmount:Int = 0
		var orderedPromotionBuyAmount:Int = 0
		var appliedPromotionBuyAmount:Int = 0

		while (normalProductBuyAmount + orderedPromotionBuyAmount <= buyAmount) {
			normalProductBuyAmount += 1

			if (normalProductBuyAmount + orderedPromotionBuyAmount >= buyAmount) {
				break
			}

			if (normalProductBuyAmount % promotionBuy == 0) {
				orderedPromotionBuyAmount += promotionGet
			}
		}

		appliedPromotionBuyAmount = orderedPromotionBuyAmount
		if (orderedPromotionBuyAmount > productStock) {
			appliedPromotionBuyAmount = productStock
		}
		return listOf<Int>(normalProductBuyAmount, orderedPromotionBuyAmount, appliedPromotionBuyAmount)
	}

	fun applyPromotionPrice(productName:String, buyAmount:Int):ApplyResult {
		var promotionProductInfo = inventoryManager.searchPromotionProduct(productName)
		var productStock = promotionProductInfo[ProductsColumn.QUANTITY]?.toInt()!!
		var productPrice = promotionProductInfo[ProductsColumn.PRICE]?.toInt()!!

		var validPromotions = getValidPromotions()
		var currentProductPromotion = validPromotions.filter { it[PromotionsColumn.NAME] == promotionProductInfo[ProductsColumn.PROMOTION]}
		var isNotOnPromotion = currentProductPromotion.isEmpty()

		//프로모션이 없을 경우
		if (isNotOnPromotion) {
			return ApplyResult(false, 0, 0)
		}

		var promotionBuy = currentProductPromotion[0][PromotionsColumn.BUY]?.toInt()!!
		var promotionGet = currentProductPromotion[0][PromotionsColumn.GET]?.toInt()!!

		//일반 재고, 예상 프로모션 적용 재고, 실제 프로모션 적용 재고 변수
		var assigned = assignBuyAmount(buyAmount, promotionBuy, promotionGet, productStock)
		var normalProductBuyAmount = assigned[0]
		var orderedPromotionBuyAmount = assigned[1]
		var appliedPromotionBuyAmount = assigned[2]

		//프로모션 재고가 없을 경우
		if (productStock <= 0) {
			return ApplyResult(false, 0, 0)
		}

		//프로모션 재고가 떨어졌을 때 정가로 안내 및 책정 (정가결제)
		if (orderedPromotionBuyAmount > appliedPromotionBuyAmount) {
			//정가로 안내 및 책정
		}

		//해당 수량보다 적게 가져온 경우 안내
		if (buyAmount % (promotionBuy+promotionGet) != 0) {
			//프로모션 안내
			var requiredAdditionalAmount = promotionBuy+promotionGet - (buyAmount % (promotionBuy+promotionGet))
			return applyPromotionPrice(productName, requiredAdditionalAmount+buyAmount)
		}

		//프로모션 적용 금액과 적용 안된 금액을 반환한다
		return ApplyResult(true, productPrice*appliedPromotionBuyAmount, appliedPromotionBuyAmount)
	}

	data class ApplyResult(
		val result: Boolean,
		val appliedPrice: Int,
		val freeGetAmount:Int
	)

	enum class Msg(val msg:String, val isSuccess: Boolean) {
		ILLEGAL_TYPE("프로모션 목록 타입이 올바른 타입이 아닙니다", false),
		ILLEGAL_TIME("종료 날짜가 시작 날짜보다 더 빠릅니다", false),
		PERIOD_OVERLAPPED("동일한 프로모션의 기간이 겹칩니다", false)
	}
}

class InputView {
	fun readItem() {
	//프로모션 재고의 이름은 내부적으로 상품 이름 + [프로모션] 이 붙게끔 설정합니다
	}
}

fun main() {
	// TODO: 프로그램 구현

}
