package store

import org.assertj.core.internal.DeepDifference.Difference
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

data class ReceiptData(
	val purchasedNormalInfo:List<Map<String,String>>,
	val purchasedPromotionInfo:List<Map<String,String>>,
	val totalAmount:Map<String,String>
)

class ErrorMessage {
	companion object {
		const val NOT_VALID_FORM = "[ERROR] 올바르지 않은 형식으로 입력했습니다. 다시 입력해 주세요."
		const val PRODUCT_NOT_FOUND = "[ERROR] 존재하지 않는 상품입니다. 다시 입력해 주세요."
		const val NOT_VALID_STOCK = "[ERROR] 재고 수량을 초과하여 구매할 수 없습니다. 다시 입력해 주세요."
	}
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
	fun searchNormalProduct(productName:String):List<Map<ProductsColumn, String>> {
		var searchResult = inventory.filter { it[ProductsColumn.NAME] == productName.trim() && it[ProductsColumn.PROMOTION] == "null"}
		return searchResult
	}

	fun searchPromotionProduct(productName:String):List<Map<ProductsColumn, String>> {
		var searchResult = inventory.filter { it[ProductsColumn.NAME] == productName.trim() && it[ProductsColumn.PROMOTION] != "null"}
		return searchResult
	}

	fun updateNormalProduct(productName: String, updateStock:String):Msg {
		inventory.forEachIndexed { index, value ->
			if (value[ProductsColumn.NAME] == productName.trim() && value[ProductsColumn.PROMOTION] == "null") {
				inventory[index][ProductsColumn.QUANTITY] = updateStock
				return Msg.SUCCESS
			}
		}
		return Msg.PRODUCT_NOT_FOUND
	}

	fun updatePromotionProduct(productName: String, updateStock:String):Msg {
		inventory.forEachIndexed { index, value ->
			if (value[ProductsColumn.NAME] == productName.trim() && value[ProductsColumn.PROMOTION] != "null") {
				inventory[index][ProductsColumn.QUANTITY] = updateStock
				return Msg.SUCCESS
			}
		}
		return Msg.PRODUCT_NOT_FOUND
	}

	companion object {
		const val PRODUCTS_DIR = "src/main/resources/products.md"
		const val PROMOTION_DIR = "src/main/resources/promotions.md"

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
												var inventoryManager: InventoryManager = InventoryManager()) {

	init {
		try {
			promotion.map {
				checkPromotionValid(it)
			}
		} catch (exception: Exception) {
				throw IllegalArgumentException(Msg.ILLEGAL_TYPE.msg)
			}

		checkPromotionDuplex()
	}

	private fun checkPromotionValid(it: MutableMap<PromotionsColumn, String>) {
		2.toUInt() / it[PromotionsColumn.BUY]?.toUInt()!! //0이 될 수 없습니다
		2.toUInt() / it[PromotionsColumn.GET]?.toUInt()!! //0이 될 수 없습니다

		val startDate = stringToLocalDate(it[PromotionsColumn.START_DATE]!!)
		val endDate = stringToLocalDate(it[PromotionsColumn.END_DATE]!!)

		//end_date가 start_date보다 빠를 수 없다
		if (startDate > endDate) {
			throw IllegalArgumentException(Msg.ILLEGAL_TIME.msg)
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
			getIsOverLapped(periods)
			}
		}

	private fun getIsOverLapped(periods:List<List<LocalDate>>) {
		var overlapped:MutableList<Boolean> = mutableListOf()
		//가능한 모든 순서쌍
		for (i in 0..<periods.size) {
			for (j in i+1..<periods.size) {
				overlapped.add(isOverlapped(periods[i], periods[j]))
			}
		}
		if (overlapped.any { it } ) {
			throw IllegalArgumentException(Msg.PERIOD_OVERLAPPED.msg)
		}
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
		while (normalProductBuyAmount + orderedPromotionBuyAmount <= buyAmount) {
			normalProductBuyAmount += 1
			if (normalProductBuyAmount + orderedPromotionBuyAmount >= buyAmount) break
			if (normalProductBuyAmount % promotionBuy == 0) orderedPromotionBuyAmount += promotionGet
		}
		return assignOrderedToApplied(normalProductBuyAmount, orderedPromotionBuyAmount, productStock)
	}

	private fun assignOrderedToApplied (normalProductBuyAmount:Int, orderedPromotionBuyAmount:Int, productStock: Int):List<Int> {
		var appliedPromotionBuyAmount = orderedPromotionBuyAmount
		if (orderedPromotionBuyAmount > productStock) {
			appliedPromotionBuyAmount = productStock
		}
		return listOf<Int>(normalProductBuyAmount, orderedPromotionBuyAmount, appliedPromotionBuyAmount)
	}

	private fun getPromotionInfo(productName:String, buyAmount: Int):PromotionInfo {
		var promotionProductInfo = inventoryManager.searchPromotionProduct(productName)
		var currentProductPromotion = getValidPromotions().filter { it[PromotionsColumn.NAME] == promotionProductInfo[0][ProductsColumn.PROMOTION]}

		return PromotionInfo(
			promotionProductInfo[0][ProductsColumn.QUANTITY]?.toInt()!!,
			promotionProductInfo[0][ProductsColumn.PRICE]?.toInt()!!,
			currentProductPromotion[0][PromotionsColumn.BUY]?.toInt()!!,
			currentProductPromotion[0][PromotionsColumn.GET]?.toInt()!!,
		)
	}

	private fun checkAvailableGetPromo (productName:String): Boolean{
		var promotionProductInfo = inventoryManager.searchPromotionProduct(productName)
		if (promotionProductInfo.isEmpty()) return false
		var currentProductPromotion = getValidPromotions().filter { it[PromotionsColumn.NAME] == promotionProductInfo[0][ProductsColumn.PROMOTION]}
		if (currentProductPromotion.isEmpty()) return false
		return true
	}

	private fun notifyPromotionNotApplied (productName: String, promoInfo:PromotionInfo, expect:Int, real:Int):ApplyResult {
		var notValidStock = expect - real
		var input = InputView.askBuyWithNoPromotion(productName, notValidStock)
		if (input == "Y") {
			return ApplyResult(true, promoInfo.Price* real, real, notValidStock*(promoInfo.Buy+promoInfo.Get))
		}
		return ApplyResult(true, promoInfo.Price* real,  real)
	}

	private fun notifyExistPromotion (productName: String, buyAmount: Int, promoInfo: PromotionInfo, expect: Int, real: Int):ApplyResult {
		var requiredAdditionalAmount = promoInfo.Buy + promoInfo.Get - (buyAmount % (promoInfo.Buy + promoInfo.Get))
		var input = InputView.askBuyPromotionProduct(productName, requiredAdditionalAmount)
		if (input == "Y") {
			var result = applyPromotionPrice(productName, requiredAdditionalAmount + buyAmount)
			result.amountDifference = requiredAdditionalAmount
			return result
		}
		return ApplyResult(true, promoInfo.Price* real,  real)

	}

	fun applyPromotionPrice(productName:String, buyAmount:Int):ApplyResult {
		if (!checkAvailableGetPromo(productName)) return ApplyResult(false, 0, 0)
		var promotionProductInfo = inventoryManager.searchPromotionProduct(productName)
		var currentProductPromotion = getValidPromotions().filter { it[PromotionsColumn.NAME] == promotionProductInfo[0][ProductsColumn.PROMOTION]}
		var promoInfo = getPromotionInfo(productName, buyAmount)
		var assigned = assignBuyAmount(buyAmount, promoInfo.Buy, promoInfo.Get, promoInfo.Stock)

		if (currentProductPromotion.isEmpty()) return ApplyResult(false, 0, 0)
		/*정가로 안내 및 책정 */
		if (assigned[1] > assigned[2]) return notifyPromotionNotApplied(productName, promoInfo, assigned[1],  assigned[2])
		//프로모션 존재 안내 및 적용
		if (buyAmount % (promoInfo.Buy+promoInfo.Get) != 0) return notifyExistPromotion(productName, buyAmount, promoInfo, assigned[1], assigned[2])
		return ApplyResult(true, promoInfo.Price* assigned[2],  assigned[2])
	}

	data class PromotionInfo (
		val Stock:Int,
		val Price:Int,
		val Buy:Int,
		val Get:Int,
	)
	data class ApplyResult(
		val result: Boolean,
		val appliedPrice: Int,
		val freeGetAmount:Int,
		var amountDifference:Int = 0
	)

	enum class Msg(val msg:String, val isSuccess: Boolean) {
		ILLEGAL_TYPE("프로모션 목록 타입이 올바른 타입이 아닙니다", false),
		ILLEGAL_TIME("종료 날짜가 시작 날짜보다 더 빠릅니다", false),
		PERIOD_OVERLAPPED("동일한 프로모션의 기간이 겹칩니다", false)
	}
}

class MemberShip {
	companion object {
		const val MEMBERSHIP_DISCOUNT_RATE = 0.3
		fun membershipDiscount(buyAmount: Int):Int {
			var discount =  (buyAmount*MEMBERSHIP_DISCOUNT_RATE).toInt()
			if (discount > 8000) return 8000
			return discount
		}
	}
}

class Receipt (private val receiptData:ReceiptData) {
	var receipt:String = ""
	private fun getPurchasedHistory():String {
		var data = receiptData.purchasedNormalInfo
		var stringData = ""
		for (i in data) {
			stringData += i["상품명"] +"\t\t"
			stringData += i["수량"] +"\t"
			stringData += i["금액"] + "\n"
		}
		return stringData
	}

	private fun getPromotionHistory():String {
		var data = receiptData.purchasedPromotionInfo
		var stringData = ""
		for (i in data) {
			stringData += i["상품명"] +"\t\t"
			stringData += i["수량"] +"\n"
		}
		return stringData
	}

	private fun getTotal():String {
		var data = receiptData.totalAmount
		var totalCount = receiptData.purchasedNormalInfo.sumOf {
			it["수량"]?.toInt()!!
		}
		var stringData = ""
		stringData += "총구매액\t\t${totalCount}\t${data["총구매액"]}\n"
		stringData += "행사할인\t\t\t${data["행사할인"]}\n"
		stringData += "멤버십할인\t\t\t${data["멤버십할인"]}\n"
		stringData += "내실돈\t\t\t    ${data["내실돈"]}\n"
		return stringData
	}
	fun createReceipt() {
		receipt += "==============W 편의점================\n"
		receipt += "상품명\t\t수량\t금액\n"
		receipt += getPurchasedHistory()
		receipt += "=============증\t정===============\n"
		receipt += getPromotionHistory()
		receipt += "====================================\n"
		receipt += getTotal()
	}
}

class InputView {
	companion object {
		fun readItem():List<List<String>> {
			OutputView.printReadItem()
			val input = camp.nextstep.edu.missionutils.Console.readLine()
			var products = input.split(",")
			products = products.map {
				it.replace("[", "").replace("]", "")
			}
			var productTable = products.map { it.split("-")}
			productTable.forEach { if(it.size != 2) throw IllegalArgumentException(ErrorMessage.NOT_VALID_FORM)}
			return productTable
		}
		fun askBuyPromotionProduct(productName: String, promotionAmount:Int):String {
			OutputView.printAskBuyPromotionProduct(productName, promotionAmount)
			val input = camp.nextstep.edu.missionutils.Console.readLine()
			if (input != "Y" && input != "N" ) throw IllegalArgumentException(ErrorMessage.NOT_VALID_FORM)
			return input
		}
		fun askBuyWithNoPromotion(productName: String, amount:Int):String {
			OutputView.printAskBuyWithNoPromotion(productName,amount)
			val input = camp.nextstep.edu.missionutils.Console.readLine()
			if (input != "Y" && input != "N" ) throw IllegalArgumentException(ErrorMessage.NOT_VALID_FORM)
			return input
		}
		fun askGetMembershipDiscount():String {
			OutputView.printAskGetMembershipDiscount()
			val input = camp.nextstep.edu.missionutils.Console.readLine()
			if (input != "Y" && input != "N" ) throw IllegalArgumentException(ErrorMessage.NOT_VALID_FORM)
			return input
		}
		fun askMoreProducts():String {
			OutputView.printAskMoreProducts()
			val input = camp.nextstep.edu.missionutils.Console.readLine()
			if (input != "Y" && input != "N" ) throw IllegalArgumentException(ErrorMessage.NOT_VALID_FORM)
			return input
		}
	}
}

class OutputView {
	companion object {
		fun printProducts(inventoryCSV:String) {
			println(inventoryCSV)
		}
		fun printWelcome() {
			println("안녕하세요. W편의점입니다.\n" +
							"현재 보유하고 있는 상품입니다.")
		}
		fun printReadItem() {
			println("구매하실 상품명과 수량을 입력해 주세요. (예: [사이다-2],[감자칩-1])")
		}
		fun printAskBuyPromotionProduct(productName:String, promotionAmount:Int) {
			println("현재 ${productName}은(는) ${promotionAmount}개를 무료로 더 받을 수 있습니다. 추가하시겠습니까? (Y/N)")
		}
		fun printAskBuyWithNoPromotion(productName:String, amount:Int) {
			println("현재 ${productName} ${amount}개는 프로모션 할인이 적용되지 않습니다. 그래도 구매하시겠습니까? (Y/N)")
		}
		fun printReceipt(receiptData: ReceiptData) {
			val receipt = Receipt(receiptData).createReceipt()
			print(receipt)
		}
		fun printAskGetMembershipDiscount() {
			println("멤버십 할인을 받으시겠습니까? (Y/N)")
		}
		fun printAskMoreProducts() {
			println("감사합니다. 구매하고 싶은 다른 상품이 있나요? (Y/N)")
		}
	}
}

class ConvenienceStore {
	var inventory:MutableList<MutableMap<ProductsColumn, String>>
	var promotion:MutableList<MutableMap<PromotionsColumn, String>>
	var inventoryManager:InventoryManager
	var promotionManager:PromotionManager

	init {
		inventory = loadInventory()
		promotion = loadPromotion()
		inventoryManager = InventoryManager(inventory)
		promotionManager = PromotionManager(promotion, inventoryManager)
	}

	private fun loadInventory():MutableList<MutableMap<ProductsColumn, String>> {
		var rawData = Files.lines(Paths.get(InventoryManager.PRODUCTS_DIR)).toList()
		return rawData.map {
			var split = it.split(",")
			mutableMapOf(
				ProductsColumn.NAME to split[0],
				ProductsColumn.PRICE to split[1],
				ProductsColumn.QUANTITY to split[2],
				ProductsColumn.PROMOTION to split[3]
			)
		}.toMutableList().subList(1, rawData.size)
	}
	private fun loadPromotion():MutableList<MutableMap<PromotionsColumn, String>>  {
		var rawData = Files.lines(Paths.get(InventoryManager.PROMOTION_DIR)).toList()
		return rawData.map {
			var split = it.split(",")
			mutableMapOf(
				PromotionsColumn.NAME to split[0],
				PromotionsColumn.BUY to split[1],
				PromotionsColumn.GET to split[2],
				PromotionsColumn.START_DATE to split[3],
				PromotionsColumn.END_DATE to split[3]
			)
		}.toMutableList().subList(1, rawData.size)
	}

	fun execute() {
		OutputView.printWelcome()
		OutputView.printProducts(inventoryManager.inventoryToCSV())
		var input = InputView.readItem()
		var results = input.map {
			var productName = it[0]
			var buyAmount = it[1].toInt()
			var isAvailable = inventoryManager.checkProductAvailable(productName)

			if(!isAvailable) {/*재입력 받기*/}
			var productInfo = inventoryManager.searchNormalProduct(productName)
			var promotionInfo = inventoryManager.searchPromotionProduct(productName)

			if (productInfo[0][ProductsColumn.QUANTITY]?.toInt()!! + promotionInfo[0][ProductsColumn.QUANTITY]?.toInt()!!< buyAmount ) {/*재고수량 초과, 재입력*/}
			var result = promotionManager.applyPromotionPrice(productName, buyAmount)

			var normalPrice = productInfo[0][ProductsColumn.PRICE]?.toInt()!! * buyAmount
			normalPrice -= result.appliedPrice
			buyAmount += result.amountDifference

		}
		//멤버쉽 할인
		//ReceiptData내용 추가
		//영수증 출력
		//재고차감 및 인벤토리 업데이트
		//재구매 여부 질문
	}
}

fun main() {
	// TODO: 프로그램 구현
	ConvenienceStore().execute()
}
