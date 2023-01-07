package school

import org.scalajs.dom.*
import scala.scalajs.js.annotation.JSExportTopLevel

@main def run = 
  document.title = "Hello Scala JS"
  setupUI

def onClick(et: EventTarget) = 
  val par = document.createElement("p")
  par.textContent = s"Button clicked ${et.asInstanceOf[HTMLBaseElement].innerHTML}"
  document.body.appendChild(par)

def setupUI = 
  val button = document.createElement("button")
  button.textContent = "Click me"
  button.addEventListener("click", _ => onClick(button))
  document.body.appendChild(button)

  
