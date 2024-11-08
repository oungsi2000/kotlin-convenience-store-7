package store

import java.nio.file.Paths
import java.nio.file.Files

enum class ProductsColumn {
	NAME,
	PRICE,
	QUANTITY,
	PROMOTION
}

enum class PromotionsColumn {
	NAME, BUY, GET, START_DATE, END_DATE
}

class InventoryManager(var inventory:MutableList<MutableMap<ProductsColumn, String>>) {
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

	fun dumpInventory() {
		//파일 입출력을 이용하여 inventory 변수의 내용을 덮어씌웁니다
		Files.write(Paths.get(PRODUCTS_DIR), inventoryToCSV().toByteArray())
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

class InputView {
	fun readItem() {
	//프로모션 재고의 이름은 내부적으로 상품 이름 + [프로모션] 이 붙게끔 설정합니다
	}
}

fun main() {
	// TODO: 프로그램 구현

}
