package models

import org.overviewproject.tree.orm.Node
import org.overviewproject.postgres.CustomTypes._
import models.core.DocumentIdList

class NodeLoader {

  import models.orm.Schema._

  def loadRootId(documentSetId: Long): Option[Long] = {
    from(nodes)(n => where(n.documentSetId === documentSetId and n.parentId === Option.empty[Long]) select (n.id)).headOption
  }

  def loadTree(documentSetId: Long, rootId: Long, depth: Int): Seq[core.Node] = {
    loadNode(documentSetId, rootId).map { root =>
      loadLevels(documentSetId, Seq(root), depth + 1, expandOtherNode = true)
    }.getOrElse(Seq.empty)
  }

  private def loadNode(documentSetId: Long, nodeId: Long): Option[core.Node] = {
    nodes.where(n => n.documentSetId === documentSetId and n.id === nodeId).headOption.map(makePartialNode)
  }

  // Recursively load depth levels of the tree, bottom up.
  private def loadLevels(documentSetId: Long, baseNodes: Seq[core.Node], depth: Int, expandOtherNode: Boolean = false): Seq[core.Node] = {
    if (depth == 0) Seq.empty
    else {
      val baseIds = baseNodes.map(_.id)
      val nextLevel = loadChildren(documentSetId, baseIds)

      val baseWithChildIds = baseNodes.map { n =>
        val childNodes = nextLevel.filter(_.parentId == Some(n.id))
        val childIds = sortNodes(childNodes).map(_.id)
        n.copy(childNodeIds = childIds)
      }
      val nodesToExpand =
        if (expandOtherNode) nextLevel
        else {
          val findOther = baseNodes.find(_.description == "(other)")

          nextLevel.filterNot(n => findOther.map(o => n.parentId == Some(o.id)).getOrElse(false))
        }

      baseWithChildIds ++ loadLevels(documentSetId, nodesToExpand.map(makePartialNode), depth - 1)
    }
  }

  private def sortNodes(nodes: Seq[Node]): Seq[Node] = {

    // bundled (other) nodes always go at end, otherwise sort by num docs, 
    def nodeOrder(a: Node, b: Node) =
      if (a.description == "(other)")
        false
      else if (b.description == "(other)")
        true
      else
        (a.cachedSize > b.cachedSize) || (a.cachedSize == b.cachedSize && a.id < b.id)

    nodes.sortWith(nodeOrder(_, _))
  }

  private def loadChildren(documentSetId: Long, nodeIds: Seq[Long]): Seq[Node] = {
    nodes.where(n => n.documentSetId === documentSetId and (n.parentId in nodeIds)).iterator.toSeq
  }

  private def makePartialNode(n: Node): core.Node =
    core.Node(n.id, n.description, Seq.empty, DocumentIdList(n.cachedDocumentIds, n.cachedSize), Map())
}