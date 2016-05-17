# Java Datalog Engine with Stratified Negation

## Notes, Features and Properties:

* It implements stratified negation, or Stratified Datalog~.
* It can parse and evaluate Datalog programs from files and Strings (actually anything that implements java.io.Reader).
* It has a fluent API through which it can be embedded in Java applications to run queries. See the main() method for examples.
* It implements "=", "<>" (alternatively "!="), "<", "<=", ">" and ">=" as built-in predicates.
* It avoids third party dependencies because it is a proof-of-concept. It should be able to stand alone.
* Values with "quoted strings" are supported, but to prevent a value like "Hello World" from being confused with a variable, it is stored internally with a " prefix, i.e. "Hello" is stored as `"Hello`. 
    This is why the `toString(Map<String, String> bindings)` method uses `substring(1)` on the term.
* Also, predicates can't be in quotes. Is this a desirable feature?

## Usage

### With Maven

The preferred method of building JDatalog is through [Maven](https://maven.apache.org/).

    # Compile like so:
    mvn package
    
    # Generate Javadocs
    mvn javadoc:javadoc

    # Run like so:
    java -jar target\jdatalog-0.0.1-SNAPSHOT.jar [filename]
    
### With Ant

An [Ant](http://ant.apache.org/) build.xml file is also provided:

    # Compile like so:
    ant 
    
    # Generate Javadocs
    ant docs
    
    # Run like so:
    java -jar dist\jdatalog-0.0.1.jar


## License

JDatalog is licensed under the [Apache license version 2](http://www.apache.org/licenses/LICENSE-2.0):

    Copyright 2015-2016 Werner Stoop
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## References:

* [wiki]  Wikipedia topic, http://en.wikipedia.org/wiki/Datalog
* [elma]  Fundamentals of Database Systems (3rd Edition); Ramez Elmasri, Shamkant Navathe
* [ceri]  What You Always Wanted to Know About Datalog (And Never Dared to Ask); Stefano Ceri, Georg Gottlob, and Letizia Tanca
* [bra1]  Deductive Databases and Logic Programming; Stefan Brass, Univ. Halle, 2009
            http://dbs.informatik.uni-halle.de/Lehre/LP09/c6_botup.pdf
* [banc]  An Amateur’s Introduction to Recursive Query Processing Strategies; Francois Bancilhon, Raghu Ramakrishnan
* [mixu]  mixu/datalog.js; Mikito Takada, https://github.com/mixu/datalog.js
* [kett]  bottom-up-datalog-js; Frederic Kettelhoit http://fkettelhoit.github.io/bottom-up-datalog-js/docs/dl.html
* [davi]  Inference in Datalog; Ernest Davis, http://cs.nyu.edu/faculty/davise/ai/datalog.html
* [gree]  Datalog and Recursive Query Processing; Todd J. Green, Shan Shan Huang, Boon Thau Loo and Wenchao Zhou
            Foundations and Trends in Databases Vol. 5, No. 2 (2012) 105–195, 2013
            http://blogs.evergreen.edu/sosw/files/2014/04/Green-Vol5-DBS-017.pdf
* [bra2]  Bottom-Up Query Evaluation in Extended Deductive Databases, Stefan Brass, 1996
            https://www.deutsche-digitale-bibliothek.de/binary/4ENXEC32EMXHKP7IRB6OKPBWSGJV5JMJ/full/1.pdf
* [sund]  Datalog Evaluation Algorithms, Dr. Raj Sunderraman, 1998
            http://tinman.cs.gsu.edu/~raj/8710/f98/alg.html
* [ull1]  Lecture notes: Datalog Rules Programs Negation; Jeffrey D. Ullman;
            http://infolab.stanford.edu/~ullman/cs345notes/cs345-1.ppt
* [ull2]  Lecture notes: Datalog Logical Rules Recursion; Jeffrey D. Ullman;
            http://infolab.stanford.edu/~ullman/dscb/pslides/dlog.ppt
* [meye]  Prolog in Python, Chris Meyers, http://www.openbookproject.net/py4fun/prolog/intro.html
* [alec]  G53RDB Theory of Relational Databases Lecture 14; Natasha Alechina;
            http://www.cs.nott.ac.uk/~psznza/G53RDB07/rdb14.pdf
* [rack]  Datalog: Deductive Database Programming, Jay McCarthy, https://docs.racket-lang.org/datalog/
            (Datalog library for the Racket language)

## Ideas and Notes

*Just some thoughts on how the system is currently implemented and how it can be improved in the future*

TODO: I could've named the program Jatalog. _Catchy!_

----

It occurred to me that if you *really* want the equivalent of JDBC *prepared statements* then you can pass pass in a `Map<String, String>` containing
the bound variables. This map will then be sent all the way down to `matchRule()` where it can be passed as the `bindings` parameter in the call
to `matchGoals()` - so the map will end up at the bottom of the StackMap stack.
This will allow you to use statements like for example `jDatalog.query(new Expr("foo","X", "Y"), binding)`, with `binding = {X : "bar"}`, in the fluent API to perform bulk inserts or queries and so on.

A problem is that the varargs ... operator must come last in the method declaration, but I can work around this by having a method that only accepts 
`List<Expr>` as an argument rather than varargs.

You can then create a method `prepareStatement()` that can return a `List<Expr>` from a parsed query.

Actually, the `List<Expr>` should be wrapped in a `JStatement` (or something) interface so that you can run insert rules, insert facts and delete facts through these *prepared statements*.

----

There are opportunities to run some of the methods in parallel using the Java 8 Streams API (I'm thinking of the calls to 
`expandStrata()` in `buildDatabase()` and the calls to `matchRule()` in `expandStrata()` in particular).

I've now gone through the effort of removing the `DatalogException`s from `expandStrata()` on down to open the road for this
implementation. `Expr#evalBuiltIn()` may throw a `RuntimeException` for one of a number of conditions which are supposed to be
caught earlier, like in `Rule#validate()`.

----

The Racket language has a Datalog module as part of its library [rack]. I've looked at its API for ideas for my own. They use the syntax `<clause>~` for a retraction, e.g `parent(bob, john)~`, which is a syntax I might want to adopt. The [rack] implementation lacks some of the features of my version, like negation and queries with multiple goals.

----

It is conceptually possible to make the `List<String> terms` of `Expr` a `List<Object>` instead, so that you can store complex Java objects in the database (as POJOs). 

The `isVariable()` method will just have to be modified to check whether its parameter is `instanceof` String and starts with an upper-case character, the bindings will become a `Map<String, Object>`, the result of `query()` will be a `List<Map<String, Object>>` and a couple of `toString()` methods will have to be modified. 

It won't be that useful a feature if you just use the parser, but it could be a *nice-to-have* if you use the fluent API. I don't intend to implement it at the moment, though.

----

I've decided against arithmetic built-in predicates, such as `plus(X,Y,Z) => X + Y = Z`, for now:

* Arithmetic predicates aren't that simple. They should be evaluated as soon as the input variables (X and Y) in this case becomes available, so that Z can be computed and bound for the remaining goals.
* Arithmetic expressions would require a more complex parser and there would be a need for `Expr` to have child `Expr` objects to build a parse tree. The parse tree would be simpler if the `terms` of `Expr` was a `List<Object>` - see my note above.

----

There are several opportunities to optimize the EDB.

You can trim facts before you start with `expandDatabase()` so that you only evaluate facts that are relevant to your goals.
So, for example, if your goal is related to "cousins" then you can filter out facts related to "employment".
Actually, you can filter out the facts in the same way that you filter the rules in `getRelevantRules()`.

The EDB can also be hidden behind an `EdbProvider` interface with a method `Collection<Expr> getFacts(String predicate)` - this way 
users of the library will be able to use different sources for the EDB, such as a SQL database, CSV or XML files. For example, an EDB that is 
backed by a database can do a `SELECT * FROM predicate` when necessary. 

This SQL idea may have a problem because you'll require statements like `query = "SELECT * FROM " + predicate;` to manage it.  

You need to retrieve all relevant facts in the `query(List<Expr> goals)`.

Such an `EdbProvider` interface will also have to have a `add(Expr e)` method that can insert facts into this back-end, for use by the 
`JDatalog#execute()` and `JDatalog#fact()` methods.

The `JDatalog#validate()` method will need to be modified as well: The `EdbProvider` will have to validate the facts itself because
facts stored in memory may have to be validated differently from facts backed by a database. My concern is that iterating through all of the
facts in a database on disk may not scale that well if the number of facts increases. See my comments in the `validate()` method. 

Perhaps the `EdbProvider` interface should extend the `Iterator` interface as well?

----

I also have the feeling that `getRelevantRules()` can somehow be replaced by `buildDependentRules()` for some simplification, but I'm
not sure how.

