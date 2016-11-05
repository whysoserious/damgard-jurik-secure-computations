# damgard-jurik-secure-computations

### Building

* Install [Scapi library](http://scapi.readthedocs.io/en/latest/install.html)
* Install Java 1.8 and SBT 0.13
* Checkout this project: https://github.com/whysoserious/damgard-jurik-secure-computations.git

### Testing

Run as usual:
```shell
sbt test
```
There are two specifications:
* [AbortSpec](src/test/scala/org/jz/iohk/AbortSpec.scala) tests behavior of Actors when a protocol timeout occurs
* [EndToEndSpec](src/test/scala/org/jz/iohk/EndToEndSpec.scala) is a property-based test which checks whether for any two numbers  whole protocol succeeds with valid zero-knowledge proofs of computations sent to Clients.
Both specs properly initialize and shutdown all Actors and ActorSystems.

Tested on Ubuntu `16.04.1 LTS` and `Mac OS X 10.11.6`.

### Running

```
sbt run
```

This [App](src/main/scala/org/jz/iohk/App.scala) is an example program showing how to deploy clients, broker and logger on 3 ActorSystems.
For convenience, I saved example output of the program in [assets/console.log](assets/console.log).
All messages sent between Actors are logged to stdio (grep for `>>>`). Whole process looks as follows:
* `Client`s (`Alice` and `Bob`) register with `Broker` (`Caroll`)
* `Broker` responds with a public key
* `Client`s send their number encrypted with a received public key
* `Broker` multiplies received numbers and sends encrypted results back to `Client`s
* `Client`s ask `Broker` for a proof of computation. In order to achieve that:
  * Each `Client` spawns a child actor - a `Verifier`.
  * `Broker` spawns a `Prover` actor for each `Verifier`.
  * Each `Verifier` sends a message to parent `Client` with information whether a proof was verified or not.
* Each `Client` prints information whether a proof was succesful or not, eg.:
```
>>> bob/verifier -> bob
       	ProofResult(true)
```
* All actors and `ActorSystem`s are shut down gracefully.

Example communication:
![Alt text](assets/sequence-diagram.png?raw=true "Title")
[SVG version](assets/sequence-diagram.svg)

Please note that order of messages may vary between each run of the program. 

### TODO

* More tests! - eg. check what happens when more than 2 clients connect, send valid messages from invalid senders etc.
* Messages sent between Actors should be encrypted. Consider configuring `ActorSystem`s to use SSL.

![](https://img.shields.io/badge/developed%20for-IOHK-blue.svg?style=flat-square) ![](https://img.shields.io/badge/coded%20on-Emacs-green.svg?style=flat-square)
