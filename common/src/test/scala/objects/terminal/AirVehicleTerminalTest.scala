// Copyright (c) 2017 PSForever
package objects.terminal

import akka.actor.ActorRef
import net.psforever.objects.serverobject.structures.{Building, StructureType}
import net.psforever.objects.{Avatar, GlobalDefinitions, Player}
import net.psforever.objects.serverobject.terminals.Terminal
import net.psforever.objects.zones.Zone
import net.psforever.packet.game.{ItemTransactionMessage, PlanetSideGUID}
import net.psforever.types.{CharacterGender, CharacterVoice, PlanetSideEmpire, TransactionType}
import org.specs2.mutable.Specification

class AirVehicleTerminalTest extends Specification {
  "Air_Vehicle_Terminal" should {
    val player = Player(Avatar("test", PlanetSideEmpire.TR, CharacterGender.Male, 0, CharacterVoice.Mute))
    val terminal = Terminal(GlobalDefinitions.air_vehicle_terminal)
    terminal.Owner = new Building(0, Zone.Nowhere, StructureType.Building)
    terminal.Owner.Faction = PlanetSideEmpire.TR

    "construct" in {
      val terminal = Terminal(GlobalDefinitions.air_vehicle_terminal)
      terminal.Actor mustEqual ActorRef.noSender
    }

    "player can buy a reaver ('lightgunship')" in {
      val msg = ItemTransactionMessage(PlanetSideGUID(1), TransactionType.Buy, 0, "lightgunship", 0, PlanetSideGUID(0))

      val reply = terminal.Request(player, msg)
      reply.isInstanceOf[Terminal.BuyVehicle] mustEqual true
      val reply2 = reply.asInstanceOf[Terminal.BuyVehicle]
      reply2.vehicle.Definition mustEqual GlobalDefinitions.lightgunship
      reply2.weapons mustEqual Nil
      reply2.inventory.length mustEqual 6
      reply2.inventory.head.obj.Definition mustEqual GlobalDefinitions.reaver_rocket
      reply2.inventory(1).obj.Definition mustEqual GlobalDefinitions.reaver_rocket
      reply2.inventory(2).obj.Definition mustEqual GlobalDefinitions.reaver_rocket
      reply2.inventory(3).obj.Definition mustEqual GlobalDefinitions.reaver_rocket
      reply2.inventory(4).obj.Definition mustEqual GlobalDefinitions.bullet_20mm
      reply2.inventory(5).obj.Definition mustEqual GlobalDefinitions.bullet_20mm
    }

    "player can not buy a fake vehicle ('reaver')" in {
      val msg = ItemTransactionMessage(PlanetSideGUID(1), TransactionType.Buy, 0, "reaver", 0, PlanetSideGUID(0))

      terminal.Request(player, msg) mustEqual Terminal.NoDeal()
    }
  }
}
