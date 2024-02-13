import javafx.application.{Application, Platform}
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.control.{Button, Label, PasswordField, TableColumn, TableView, TextField, Tooltip}
import javafx.scene.layout.{HBox, VBox}
import javafx.stage.{Modality, Stage}
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.{Document, MongoClient, MongoCollection}

import scala.util.Try
import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import javafx.scene.layout.{GridPane, VBox}
import org.controlsfx.control.Notifications
import javafx.beans.property.SimpleIntegerProperty
import org.mongodb.scala.model.Filters.equal

import scala.concurrent.ExecutionContext.Implicits.global
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.{Alert, ButtonType}
import javafx.stage.FileChooser
import javafx.stage.FileChooser.ExtensionFilter

object ConfirmDialog {
  def showAndWait(title: String, message: String): Boolean = {
    val alert = new Alert(AlertType.CONFIRMATION)
    alert.setTitle(title)
    alert.setHeaderText(null)
    alert.setContentText(message)

    val result = alert.showAndWait()
    result.isPresent && result.get() == ButtonType.OK
  }
}
import java.io.{BufferedWriter, FileWriter}
import javafx.scene.control.TableView

object CsvExporter {
  def exportToCsv(tableView: TableView[_], filePath: String): Unit = {
    try {
      val writer = new BufferedWriter(new FileWriter(filePath))

      // Write header
      val header = tableView.getColumns.toArray.map(_.asInstanceOf[javafx.scene.control.TableColumn[_, _]].getText).mkString(",")
      writer.write(header)
      writer.newLine()

      // Write data
      tableView.getItems.forEach(item => {
        val row = tableView.getColumns.toArray.map { col =>
          col.asInstanceOf[javafx.scene.control.TableColumn[Any, Any]].getCellData(item)
        }.mkString(",")
        writer.write(row)
        writer.newLine()
      })

      writer.close()
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
  }
}

object Main extends App {
  Application.launch(classOf[MyJavaFxApp], args: _*)
}

class MyJavaFxApp extends Application {
  val tableView = createProductTableView()

  override def start(primaryStage: Stage): Unit = {
    val root = new VBox(10) // Vertical box with 10 pixels spacing
    val scene = new Scene(root, 600, 300)
    primaryStage.setTitle("Gestion des stocks")
    primaryStage.setScene(scene)

    // Create login page
    val loginPane = new LoginPane

    // Set styles using CSS (ensure proper resource loading)
    val cssUrl = getClass.getResource("/styles.css")
    if (cssUrl != null) {
      scene.getStylesheets.add(cssUrl.toExternalForm)
    } else {
      println("CSS file not found!")
    }
    val fontFileUrl = getClass.getResource("/fonts/IBMPlexSans-Regular.ttf")
    if (fontFileUrl != null) {
      scene.getStylesheets.add(s"@font-face { font-family: 'IBMPlexSans-Regular'; src: url('${fontFileUrl.toExternalForm}'); }")
    } else {
      println("Font file not found!")
    }

    // Switch to the main application when login succeeds
    loginPane.onLoginSuccess = () => {
      primaryStage.setScene(createMainScene())
      primaryStage.setTitle("Stock Management App - Welcome")
    }

    root.getChildren.add(loginPane)

    primaryStage.show()
  }

  def showNotification(message: String): Unit = {
    // Create and show a notification
    Notifications.create()
      .title("Notification")
      .text(message)
      .darkStyle()
      .show()
  }

  private def createUpdateProductForm(product: Product): Scene = {
    val formRoot = new GridPane
    val formScene: Scene = new Scene(formRoot, 500, 400)

    formRoot.setAlignment(Pos.CENTER)
    formRoot.setHgap(10)
    formRoot.setVgap(10)

    val cssUrl = getClass.getResource("/styles.css")
    if (cssUrl != null) {
      formScene.getStylesheets.add(cssUrl.toExternalForm)
    } else {
      println("CSS file not found!")
    }

    // Existing code...
    val productNameLabel = new Label("Nom du produit:")
    val productNameTextField = new TextField()

    val descriptionLabel = new Label("Description du produit:")
    val descriptionTextField = new TextField()

    val priceLabel = new Label("Prix:")
    val priceTextField = new TextField()

    val quantityLabel = new Label("Quantité en stock:")
    val quantityTextField = new TextField()

    val categoryLabel = new Label("Catégorie:")
    val categoryTextField = new TextField()

    val productCodeLabel = new Label("Code produit:")
    val productCodeTextField = new TextField()

    val supplierLabel = new Label("Fournisseur:")
    val supplierTextField = new TextField()

    val dateAddedLabel = new Label("Date d'ajout:")
    val dateAddedTextField = new TextField()

    // Initialize text fields with the selected product data
    productNameTextField.setText(product.getProductName)
    descriptionTextField.setText(product.getDescription)
    priceTextField.setText(product.getPrice.toString)
    quantityTextField.setText(product.getQuantity.toString)
    categoryTextField.setText(product.getCategory)
    productCodeTextField.setText(product.getProductCode)
    supplierTextField.setText(product.getSupplier)
    dateAddedTextField.setText(product.getDateAdded)

    // Existing code...

    // Add a method to show a notification
    def showNotification(message: String): Unit = {
      // Create and show a notification
      Notifications.create()
        .title("Notification")
        .text(message)
        .darkStyle()
        .show()
    }

    val saveChangesButton = new Button("Save Changes")
    saveChangesButton.setOnAction(_ => {
      // Get updated values from text fields
      val updatedProductName = productNameTextField.getText
      val updatedDescription = descriptionTextField.getText
      val updatedPrice = Try(priceTextField.getText.toDouble).getOrElse(0.0)
      val updatedQuantity = Try(quantityTextField.getText.toInt).getOrElse(0)
      val updatedCategory = categoryTextField.getText
      val updatedProductCode = productCodeTextField.getText
      val updatedSupplier = supplierTextField.getText
      val updatedDateAdded = dateAddedTextField.getText

      // Update the product in MongoDB with the new values
      val mongoClient = MongoClient("mongodb://localhost:27017")
      val database = mongoClient.getDatabase("Stock")
      val collection: MongoCollection[Document] = database.getCollection("products")

      val filter = equal("productId", product.getProductId)

      val update = Document(
        "$set" -> Document(
          "productName" -> updatedProductName,
          "description" -> updatedDescription,
          "price" -> updatedPrice,
          "quantity" -> updatedQuantity,
          "category" -> updatedCategory,
          "productCode" -> updatedProductCode,
          "supplier" -> updatedSupplier,
          "dateAdded" -> updatedDateAdded
        )
      )

      collection.updateOne(filter, update).toFuture().onComplete {
        case Success(result) =>
          Platform.runLater(() => {
            println(s"Product updated in MongoDB: ${result.getMatchedCount} document(s) matched.")
            // Close the current window
            val stage = saveChangesButton.getScene.getWindow.asInstanceOf[Stage]
            stage.close()

            // Show a success notification
            showNotification("Product updated successfully")

            // Add any additional logic or UI updates as needed
            mongoClient.close()
          })

        case Failure(exception) =>
          Platform.runLater(() => {
            println(s"Error updating product in MongoDB: ${exception.getMessage}")
            // Handle the error
            mongoClient.close()
          })
      }(ExecutionContext.global)
    })

    formRoot.add(saveChangesButton, 1, 9)
    formRoot.add(productNameLabel, 0, 0)
    formRoot.add(productNameTextField, 1, 0)
    formRoot.add(descriptionLabel, 0, 1)
    formRoot.add(descriptionTextField, 1, 1)
    formRoot.add(priceLabel, 0, 2)
    formRoot.add(priceTextField, 1, 2)
    formRoot.add(quantityLabel, 0, 3)
    formRoot.add(quantityTextField, 1, 3)
    formRoot.add(categoryLabel, 0, 4)
    formRoot.add(categoryTextField, 1, 4)
    formRoot.add(productCodeLabel, 0, 5)
    formRoot.add(productCodeTextField, 1, 5)
    formRoot.add(supplierLabel, 0, 6)
    formRoot.add(supplierTextField, 1, 6)
    formRoot.add(dateAddedLabel, 0, 7)
    formRoot.add(dateAddedTextField, 1, 7)



    formScene
  }

  // Create the main scene after successful login
  private def createMainScene(): Scene = {
    val root = new VBox(10)
    val scene = new Scene(root, 800, 600)

    val cssUrl = getClass.getResource("/styles.css")
    if (cssUrl != null) {
      scene.getStylesheets.add(cssUrl.toExternalForm)
    } else {
      println("CSS file not found!")
    }

    // Create form components for stock management
    val labelWelcome = new Label("Welcome to Stock Management App!")

    // Create TableView for product data

    // Create CRUD buttons
    val btnCreate = createSquareButton("Créer", () => {
      val productFormStage = new Stage()
      productFormStage.initModality(Modality.APPLICATION_MODAL)
      productFormStage.setTitle("Créer un produit")
      productFormStage.setScene(createProductForm())
      productFormStage.showAndWait()
    })
    val btnUpdate = createSquareButton("mettre à jour",()=>{
      // Get the selected product from the TableView
      val selectedProduct = tableView.getSelectionModel.getSelectedItem

      if (selectedProduct != null) {
        // Show the update form with the selected product data
        val updateProductStage = new Stage()
        updateProductStage.initModality(Modality.APPLICATION_MODAL)
        updateProductStage.setTitle("Mettre à Jour le Produit")
        updateProductStage.setScene(createUpdateProductForm(selectedProduct))
        updateProductStage.showAndWait()

        // After the update form is closed, refresh the TableView
        updateTableView(tableView)
      } else {
        // Show a notification if no product is selected
        showNotification("Veuillez sélectionner un produit à mettre à jour.")
      }

    })
    val btnDelete = createSquareButton("Supprimer", () => {
      // Get the selected product from the TableView
      val selectedProduct = tableView.getSelectionModel.getSelectedItem

      if (selectedProduct != null) {
        // Show a confirmation dialog before deleting the product
        val confirmation = ConfirmDialog.showAndWait("Confirmation", "Voulez-vous vraiment supprimer ce produit?")
        if (confirmation) {
          // Delete the product from MongoDB
          val mongoClient = MongoClient("mongodb://localhost:27017")
          val database = mongoClient.getDatabase("Stock")
          val collection: MongoCollection[Document] = database.getCollection("products")

          val filter = equal("productId", selectedProduct.getProductId)

          collection.deleteOne(filter).toFuture().onComplete {
            case Success(result) =>
              Platform.runLater(() => {
                println(s"Product deleted from MongoDB: ${result.getDeletedCount} document(s) deleted.")
                // Show a success notification
                showNotification("Produit supprimé avec succès")

                // Refresh the TableView after deletion
                updateTableView(tableView)

                // Add any additional logic or UI updates as needed
                mongoClient.close()
              })

            case Failure(exception) =>
              Platform.runLater(() => {
                println(s"Error deleting product from MongoDB: ${exception.getMessage}")
                // Handle the error
                mongoClient.close()
              })
          }(ExecutionContext.global)
        }
      } else {
        // Show a notification if no product is selected
        showNotification("Veuillez sélectionner un produit à supprimer.")
      }
    })

    val btnExporterCsv = createSquareButton("Exporter les données en Format CSV", () => {
      val fileChooser = new FileChooser()
      fileChooser.setTitle("Export CSV File")
      fileChooser.getExtensionFilters.add(new ExtensionFilter("CSV Files", "*.csv"))

      // Show save dialog
      val file = fileChooser.showSaveDialog(null)

      if (file != null) {
        // User selected a file, proceed with export
        CsvExporter.exportToCsv(tableView, file.getAbsolutePath)
        // Optionally, show a notification or confirmation message
        showNotification("Données exportées avec succès en format CSV.")
      }
    })


    // Place "Update" and "Delete" buttons in an HBox for horizontal alignment
    val buttonsHBox = new HBox(10) // 10 pixels spacing between buttons
    buttonsHBox.getChildren.addAll(btnCreate,btnUpdate,btnDelete,btnExporterCsv)
    buttonsHBox.setAlignment(Pos.CENTER) // Center align the HBox

    // Add components to the layout
    root.getChildren.addAll(labelWelcome, tableView, buttonsHBox)

    scene
  }
  case class Product(
                      productId: Integer,
                      productName: String,
                      description: String,
                      price: Double,
                      quantity: Int,
                      category: String,
                      productCode: String,
                      supplier: String,
                      dateAdded: String
                    ) {
    // Explicit getter methods for JavaFX PropertyValueFactory
    def getProductId: Integer = productId
    def getProductName: String = productName
    def getDescription: String = description
    def getPrice: Double = price
    def getQuantity: Int = quantity
    def getCategory: String = category
    def getProductCode: String = productCode
    def getSupplier: String = supplier
    def getDateAdded: String = dateAdded
  }

  // Utility method to create a TableView for product data
  private def createProductTableView(): TableView[Product] = {
    val tableView = new TableView[Product]

    // Define columns for TableView

    val colProductId = new TableColumn[Product, Integer]("Product ID")
    colProductId.setCellValueFactory(cellData => new SimpleIntegerProperty(cellData.getValue.getProductId).asObject())


    val colProductName = new TableColumn[Product, String]("Nom de produit")
    colProductName.setCellValueFactory(new PropertyValueFactory[Product, String]("productName"))

    val colDescription = new TableColumn[Product, String]("Description")
    colDescription.setCellValueFactory(new PropertyValueFactory[Product, String]("description"))

    val colPrice = new TableColumn[Product, Double]("prix")
    colPrice.setCellValueFactory(new PropertyValueFactory[Product, Double]("price"))

    val colQuantity = new TableColumn[Product, Int]("Quantité")
    colQuantity.setCellValueFactory(new PropertyValueFactory[Product, Int]("quantity"))

    val colCategory = new TableColumn[Product, String]("Catégorie")
    colCategory.setCellValueFactory(new PropertyValueFactory[Product, String]("category"))

    val colProductCode = new TableColumn[Product, String]("code produit")
    colProductCode.setCellValueFactory(new PropertyValueFactory[Product, String]("productCode"))

    val colSupplier = new TableColumn[Product, String]("Fournisseur")
    colSupplier.setCellValueFactory(new PropertyValueFactory[Product, String]("supplier"))

    val colDateAdded = new TableColumn[Product, String]("date ajoutée")
    colDateAdded.setCellValueFactory(new PropertyValueFactory[Product, String]("dateAdded"))

    // Add columns to TableView
    tableView.getColumns.addAll(
      colProductId, colProductName, colDescription, colPrice,
      colQuantity, colCategory, colProductCode, colSupplier, colDateAdded
    )
    val database = MongoClient("mongodb://localhost:27017").getDatabase("Stock")
    // Fetch product data from MongoDB and add it to the TableView
    val collection: MongoCollection[Document] = database.getCollection("products")

    // Fetch all products from the collection
    collection.find().toFuture().onComplete {
      case Success(products) =>
        // Map MongoDB documents to Product objects
        val productList: Seq[Product] = products.map { doc =>
          Product(
            doc.getInteger("productId"),
            doc.getString("productName"),
            doc.getString("description"),
            doc.getDouble("price"),
            doc.getInteger("quantity"),
            doc.getString("category"),
            doc.getString("productCode"),
            doc.getString("supplier"),
            doc.getString("dateAdded")
          )
        }

        // Logging to check the retrieved data
        productList.foreach(println)

        // Add data to TableView on JavaFX Application Thread
        Platform.runLater(() => {
          tableView.getItems.addAll(productList: _*)
        })


      case Failure(exception) =>
        // Print detailed error on the console
        Platform.runLater(() => {
          exception.printStackTrace()
          println(s"Error fetching products from MongoDB: ${exception.getMessage}")
        })
    }

    tableView
  }
  import scala.util.Random

  object ProductIdGenerator {
    private val random = new Random()

    // Function to generate a unique product ID with a random part
    def uniqueProductId(): Int = {
      val randomPart = random.nextInt(100000) // Generates a random number with at most 5 digits
      randomPart
    }
  }
  def updateTableView(tableView: TableView[Product]): Unit = {
    val database = MongoClient("mongodb://localhost:27017").getDatabase("Stock")
    val collection: MongoCollection[Document] = database.getCollection("products")

    // Fetch all products from the collection
    collection.find().toFuture().onComplete {
      case Success(products) =>
        // Map MongoDB documents to Product objects
        val productList: Seq[Product] = products.map { doc =>
          Product(
            doc.getInteger("productId"),
            doc.getString("productName"),
            doc.getString("description"),
            doc.getDouble("price"),
            doc.getInteger("quantity"),
            doc.getString("category"),
            doc.getString("productCode"),
            doc.getString("supplier"),
            doc.getString("dateAdded")
          )
        }

        // Clear existing items in the TableView
        Platform.runLater(() => {
          tableView.getItems.clear()
        })

        // Add new data to TableView on JavaFX Application Thread
        Platform.runLater(() => {
          tableView.getItems.addAll(productList: _*)
        })

      case Failure(exception) =>
        // Print detailed error on the console
        Platform.runLater(() => {
          exception.printStackTrace()
          println(s"Error fetching products from MongoDB: ${exception.getMessage}")
        })
    }(ExecutionContext.global)
  }

  // Utility method to create the product creation form
  private def createProductForm(): Scene = {
    val formRoot = new GridPane
    formRoot.setAlignment(Pos.CENTER)
    formRoot.setHgap(10)
    formRoot.setVgap(10)

    val formScene = new Scene(formRoot, 500, 400)

    val cssUrl = getClass.getResource("/styles.css")
    if (cssUrl != null) {
      formScene.getStylesheets.add(cssUrl.toExternalForm)
    } else {
      println("CSS file not found!")
    }

    val productNameLabel = new Label("Nom du produit:")
    val productNameTextField = new TextField()

    val descriptionLabel = new Label("Description du produit:")
    val descriptionTextField = new TextField()

    val priceLabel = new Label("Prix:")
    val priceTextField = new TextField()

    val quantityLabel = new Label("Quantité en stock:")
    val quantityTextField = new TextField()

    val categoryLabel = new Label("Catégorie:")
    val categoryTextField = new TextField()

    val productCodeLabel = new Label("Code produit:")
    val productCodeTextField = new TextField()

    val supplierLabel = new Label("Fournisseur:")
    val supplierTextField = new TextField()

    val dateAddedLabel = new Label("Date d'ajout:")
    val dateAddedTextField = new TextField()

    // Apply styling to the labels
    productNameLabel.getStyleClass.add("form-label")
    descriptionLabel.getStyleClass.add("form-label")
    priceLabel.getStyleClass.add("form-label")
    quantityLabel.getStyleClass.add("form-label")
    categoryLabel.getStyleClass.add("form-label")
    productCodeLabel.getStyleClass.add("form-label")
    supplierLabel.getStyleClass.add("form-label")
    dateAddedLabel.getStyleClass.add("form-label")

    // Apply styling to the text fields
    productNameTextField.getStyleClass.add("form-text-field")
    descriptionTextField.getStyleClass.add("form-text-field")
    priceTextField.getStyleClass.add("form-text-field")
    quantityTextField.getStyleClass.add("form-text-field")
    categoryTextField.getStyleClass.add("form-text-field")
    productCodeTextField.getStyleClass.add("form-text-field")
    supplierTextField.getStyleClass.add("form-text-field")
    dateAddedTextField.getStyleClass.add("form-text-field")

    // Set responsive layout for text fields
    productNameTextField.setPrefWidth(200)
    descriptionTextField.setPrefWidth(200)
    priceTextField.setPrefWidth(200)
    quantityTextField.setPrefWidth(200)
    categoryTextField.setPrefWidth(200)
    productCodeTextField.setPrefWidth(200)
    supplierTextField.setPrefWidth(200)
    dateAddedTextField.setPrefWidth(200)

    // Set padding and margin for text fields
    productNameTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    descriptionTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    priceTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    quantityTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    categoryTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    productCodeTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    supplierTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")
    dateAddedTextField.setStyle("-fx-padding: 5px; -fx-margin: 5px;")

    // Add a method to show a notification
    def showNotification(message: String): Unit = {
      // Create and show a notification
      Notifications.create()
        .title("Notification")
        .text(message)
        .darkStyle()
        .show()
    }


    val saveButton = new Button("Save")
    saveButton.setOnAction(_ => {
      val productName = productNameTextField.getText
      val description = descriptionTextField.getText
      val price = Try(priceTextField.getText.toDouble).toOption.getOrElse(0.0)
      val quantity = Try(quantityTextField.getText.toInt).toOption.getOrElse(0)
      val category = categoryTextField.getText
      val productCode = productCodeTextField.getText
      val supplier = supplierTextField.getText
      val dateAdded = dateAddedTextField.getText

      if (productName.isEmpty || priceTextField.getText.isEmpty || quantityTextField.getText.isEmpty) {
        // Show a notification to the user
        showNotification("Veuillez remplir tous les champs requis")
      } else {
        // Connect to MongoDB and save the product
        val mongoClient = MongoClient("mongodb://localhost:27017")
        val database = mongoClient.getDatabase("Stock")
        val collection: MongoCollection[Document] = database.getCollection("products")

        val productDocument = Document(
          "productId" -> ProductIdGenerator.uniqueProductId(),
          "productName" -> productName,
          "description" -> description,
          "price" -> price,
          "quantity" -> quantity,
          "category" -> category,
          "productCode" -> productCode,
          "supplier" -> supplier,
          "dateAdded" -> dateAdded
        )

        collection.insertOne(productDocument).toFuture().onComplete {
          case Success(result: InsertOneResult) =>
            Platform.runLater(() => {
              println(s"Product saved to MongoDB: ${result.getInsertedId}")
              // Close the current window
              val stage = saveButton.getScene.getWindow.asInstanceOf[Stage]
              stage.close()

              // Show a success notification
              showNotification("Product saved successfully")

              updateTableView(tableView)


              // Add any additional logic or UI updates as needed
              mongoClient.close()
            })

          case Failure(exception) =>
            Platform.runLater(() => {
              println(s"Error saving product to MongoDB: ${exception.getMessage}")
              // Handle the error
              mongoClient.close()
            })
        }(ExecutionContext.global)
      }
    })

    // Add components to the form layout
    formRoot.add(productNameLabel, 0, 0)
    formRoot.add(productNameTextField, 1, 0)
    formRoot.add(descriptionLabel, 0, 1)
    formRoot.add(descriptionTextField, 1, 1)
    formRoot.add(priceLabel, 0, 2)
    formRoot.add(priceTextField, 1, 2)
    formRoot.add(quantityLabel, 0, 3)
    formRoot.add(quantityTextField, 1, 3)
    formRoot.add(categoryLabel, 0, 4)
    formRoot.add(categoryTextField, 1, 4)
    formRoot.add(productCodeLabel, 0, 5)
    formRoot.add(productCodeTextField, 1, 5)
    formRoot.add(supplierLabel, 0, 6)
    formRoot.add(supplierTextField, 1, 6)
    formRoot.add(dateAddedLabel, 0, 7)
    formRoot.add(dateAddedTextField, 1, 7)
    formRoot.add(saveButton, 1, 8)

    formScene
  }

  // Utility method to create square buttons
  private def createSquareButton(text: String, action: () => Unit): Button = {
    val button = new Button(text)
    button.getStyleClass.add("square-button")
    button.setStyle("-fx-padding: 10px 15px; -fx-margin: 5px;") // Set padding and margin

    // Handle button click events
    button.setOnAction(_ => action())

    button
  }
}







class LoginPane extends VBox {
  getStyleClass.add("vbox-form")
  private val usernameLabel = new Label("Username:")
  private val usernameTextField = new TextField()

  private val passwordLabel = new Label("Password:")
  private val passwordField = new PasswordField()

  private val loginButton = new Button("Login")

  // Callback for successful login
  var onLoginSuccess: () => Unit = _

  // Initialize the LoginPane
  initLoginPane()

  // Configure the layout and handle button click event
  private def initLoginPane(): Unit = {
    setSpacing(10)
    getChildren.addAll(usernameLabel, usernameTextField, passwordLabel, passwordField, loginButton)

    loginButton.setOnAction(_ => {
      val username = usernameTextField.getText
      val password = passwordField.getText

      // Perform validation (e.g., check against a predefined username and password)
      if (isValidLogin(username, password)) {
        // Call the callback for successful login
        onLoginSuccess()
      } else {
        // Show an error message or take appropriate action for invalid login
        println("Invalid login credentials!")
      }
    })
  }

  // Simple validation (replace with your authentication logic)
  private def isValidLogin(username: String, password: String): Boolean = {
    // Replace with your authentication logic
    username == "" && password == ""
  }
}
