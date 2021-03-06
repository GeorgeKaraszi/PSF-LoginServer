// Copyright (c) 2017 PSForever
package net.psforever.objects

import net.psforever.objects.ce.SimpleDeployable
import net.psforever.objects.definition.DeployableDefinition

class ExplosiveDeployable(cdef : DeployableDefinition) extends SimpleDeployable(cdef) {
  private var exploded : Boolean = false

  def Exploded : Boolean = exploded

  def Exploded_=(fuse : Boolean) : Boolean = {
    exploded = fuse
    Exploded
  }
}
