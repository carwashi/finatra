package com.twitter.finatra

import com.twitter.finagle.{Service, SimpleFilter}
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import com.twitter.util.Future
import com.twitter.finagle.http.{Request => FinagleRequest, Response => FinagleResponse}

import org.apache.commons.io.IOUtils
import java.io.{FileInputStream, File, InputStream}
import javax.activation.MimetypesFileTypeMap
import com.twitter.app.App

object FileResolver {

  def hasFile(path: String): Boolean = {
    if(config.env() == "production"){
      hasResourceFile(path)
    } else {
      hasLocalFile(path)
    }
  }

  def getInputStream(path: String): InputStream = {
    if(config.env() == "production"){
      getResourceInputStream(path)
    } else {
      getLocalInputStream(path)
    }
  }

  private def getResourceInputStream(path: String): InputStream =
    getClass.getResourceAsStream(path)

  private def getLocalInputStream(path: String): InputStream = {
    val file = new File(config.docroot(), path)

    new FileInputStream(file)
  }

  private def hasResourceFile(path: String): Boolean = {
    val fi      = getClass.getResourceAsStream(path)
    var result  = false

    try {
      if (fi != null && fi.available > 0) {
        result = true
      } else {
        result = false
      }
    } catch {
      case e: Exception =>
        result = false
    }
    result
  }

  private def hasLocalFile(path: String): Boolean = {
    val file = new File(config.docroot(), path)

    if(file.toString.contains(".."))     return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}

object FileService {

  def getContentType(str: String): String = {
    extMap.getContentType(str)
  }

  def getContentType(file: File): String = {
    extMap.getContentType(file)
  }

  lazy val extMap = new MimetypesFileTypeMap(
    FileService.getClass.getResourceAsStream("/META-INF/mime.types")
  )

}

class FileService extends SimpleFilter[FinagleRequest, FinagleResponse] with App with Logging {

  def isValidPath(path: String): Boolean = {
    val fi      = getClass.getResourceAsStream(path)
    var result  = false

    try {
      if (fi != null && fi.available > 0) {
        result = true
      } else {
        result = false
      }
    } catch {
      case e: Exception =>
        result = false
    }
    result
  }

  def apply(request: FinagleRequest, service: Service[FinagleRequest, FinagleResponse]): Future[FinagleResponse] = {
    val path = new File(config.assetPath(), request.path).toString
    if (FileResolver.hasFile(path) && request.path != '/') {
      val fh  = FileResolver.getInputStream(path)
      val b   = IOUtils.toByteArray(fh)

      fh.read(b)

      val response  = request.response
      val mtype     = FileService.extMap.getContentType('.' + request.path.toString.split('.').last)

      response.status = OK
      response.setHeader("Content-Type", mtype)
      response.setContent(copiedBuffer(b))

      Future.value(response)
    } else {
      service(request)
    }
  }
}