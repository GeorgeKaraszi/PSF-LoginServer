// Copyright (c) 2017 PSForever
package net.psforever.objects.serverobject.terminals

import akka.actor.{Actor, ActorRef, Cancellable}
import net.psforever.objects._
import net.psforever.objects.serverobject.CommonMessages
import net.psforever.objects.serverobject.affinity.{FactionAffinity, FactionAffinityBehavior}
import services.{Service, ServiceManager}

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * An `Actor` that handles messages being dispatched to a specific `ProximityTerminal`.
  * Although this "terminal" itself does not accept the same messages as a normal `Terminal` object,
  * it returns the same type of messages - wrapped in a `TerminalMessage` - to the `sender`.
  * @param term the proximity unit (terminal)
  */
class ProximityTerminalControl(term : Terminal with ProximityUnit) extends Actor with FactionAffinityBehavior.Check {
  var service : ActorRef = ActorRef.noSender
  var terminalAction : Cancellable = DefaultCancellable.obj
  val callbacks : mutable.ListBuffer[ActorRef] = new mutable.ListBuffer[ActorRef]()
  val log = org.log4s.getLogger

  def FactionObject : FactionAffinity = term

  def TerminalObject : Terminal with ProximityUnit = term

  def receive : Receive = Start

  def Start : Receive = checkBehavior
    .orElse {
    case Service.Startup() =>
      ServiceManager.serviceManager ! ServiceManager.Lookup("local")

    case ServiceManager.LookupResult("local", ref) =>
      service = ref
      context.become(Run)

    case _ => ;
  }

  def Run : Receive = checkBehavior
    .orElse {
      case CommonMessages.Use(_, Some(target : PlanetSideGameObject)) =>
        if(term.Definition.asInstanceOf[ProximityDefinition].Validations.exists(p => p(target))) {
          Use(target, term.Continent, sender)
        }

      case CommonMessages.Use(_, Some((target : PlanetSideGameObject, callback : ActorRef))) =>
        if(term.Definition.asInstanceOf[ProximityDefinition].Validations.exists(p => p(target))) {
          Use(target, term.Continent, callback)
        }

      case CommonMessages.Use(_, _) =>
        log.warn(s"unexpected format for CommonMessages.Use in this context")

      case CommonMessages.Unuse(_, Some(target : PlanetSideGameObject)) =>
        Unuse(target, term.Continent)

      case CommonMessages.Unuse(_, _) =>
        log.warn(s"unexpected format for CommonMessages.Unuse in this context")

      case ProximityTerminalControl.TerminalAction() =>
        val proxDef = term.Definition.asInstanceOf[ProximityDefinition]
        val validateFunc : PlanetSideGameObject=>Boolean = term.Validate(proxDef.UseRadius * proxDef.UseRadius, proxDef.Validations)
        val callbackList = callbacks.toList
        term.Targets.zipWithIndex.foreach({ case((target, index)) =>
          if(validateFunc(target)) {
            callbackList.lift(index) match {
              case Some(cback) =>
                cback ! ProximityUnit.Action(term, target)
              case None =>
                log.error(s"improper callback registered for $target on $term in zone ${term.Owner.Continent}; this may be recoverable")
            }
          }
          else {
            Unuse(target, term.Continent)
          }
        })

      case CommonMessages.Hack(player) =>
        term.HackedBy = player
        sender ! true

      case CommonMessages.ClearHack() =>
        term.HackedBy = None

      case ProximityUnit.Action(_, _) =>
        //reserved

      case msg =>
        log.warn(s"unexpected message $msg")
    }

  def Use(target : PlanetSideGameObject, zone : String, callback : ActorRef) : Unit = {
    val hadNoUsers = term.NumberUsers == 0
    if(term.AddUser(target)) {
      log.info(s"ProximityTerminal.Use: unit ${term.Definition.Name}@${term.GUID.guid} will act on $target")
      //add callback
      callbacks += callback
      //activation
      if(term.NumberUsers == 1 && hadNoUsers) {
        val medDef = term.Definition.asInstanceOf[MedicalTerminalDefinition]
        import scala.concurrent.ExecutionContext.Implicits.global
        terminalAction.cancel
        terminalAction = context.system.scheduler.schedule(500 milliseconds, medDef.Interval, self, ProximityTerminalControl.TerminalAction())
        service ! Terminal.StartProximityEffect(term)
      }
    }
    else {
      log.warn(s"ProximityTerminal.Use: $target was rejected by unit ${term.Definition.Name}@${term.GUID.guid}")
    }
  }

  def Unuse(target : PlanetSideGameObject, zone : String) : Unit = {
    val whereTarget = term.Targets.indexWhere(_ eq target)
    val previousUsers = term.NumberUsers
    val hadUsers = previousUsers > 0
    if(whereTarget > -1 && term.RemoveUser(target)) {
      log.info(s"ProximityTerminal.Unuse: unit ${term.Definition.Name}@${term.GUID.guid} will cease operation on $target")
      //remove callback
      callbacks.remove(whereTarget)
      //de-activation (global / local)
      if(term.NumberUsers == 0 && hadUsers) {
        terminalAction.cancel
        service ! Terminal.StopProximityEffect(term)
      }
    }
    else {
      log.debug(s"ProximityTerminal.Unuse: target by proximity $target is not known to $term, though the unit tried to 'Unuse' it")
    }
  }

  override def toString : String = term.Definition.Name
}

object ProximityTerminalControl {
  object Validation {
    def Medical(target : PlanetSideGameObject) : Boolean = target match {
      case p : Player =>
        p.Health > 0 && (p.Health < p.MaxHealth || p.Armor < p.MaxArmor)
      case _ =>
        false
    }

    def HealthCrystal(target : PlanetSideGameObject) : Boolean = target match {
      case p : Player =>
        p.Health > 0 && p.Health < p.MaxHealth
      case _ =>
        false
    }

    def RepairSilo(target : PlanetSideGameObject) : Boolean = target match {
      case v : Vehicle =>
        !GlobalDefinitions.isFlightVehicle(v.Definition) && v.Health > 0 && v.Health < v.MaxHealth
      case _ =>
        false
    }

    def PadLanding(target : PlanetSideGameObject) : Boolean = target match {
      case v : Vehicle =>
        GlobalDefinitions.isFlightVehicle(v.Definition) && v.Health > 0 && v.Health < v.MaxHealth
      case _ =>
        false
    }
  }

  private case class TerminalAction()
}
