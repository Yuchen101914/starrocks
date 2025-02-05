<!-- 
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

## StarRocks Develop Environment based on docker

### Preparation

1. Download the StarRocks code repo

    ```console
    $ cd /to/your/workspace/
    $ git clone https://github.com/apache/incubator-doris.git
    ```

1. Copy Dockerfile

    ```console
    $ cd /to/your/workspace/
    $ cp incubator-doris/docker/Dockerfile ./
    ```

1. Download Oracle JDK(1.8+) RPM

    You need to download the Oracle JDK RPM, which can be found [here][1]. And
    rename it to `jdk.rpm`.

After preparation, your workspace should like this:

```
.
├── Dockerfile
├── incubator-doris
│   ├── be
│   ├── bin
│   ├── build.sh
│   ├── conf
│   ├── DISCLAIMER-WIP
│   ├── docker
│   ├── docs
│   ├── env.sh
│   ├── fe
│   ├── ...
├── jdk.rpm
```

### Build docker image

```console
$ cd /to/your/workspace/
$ docker build -t doris:v1.0  .
```

> `doris` is docker image repository name and `v1.0` is tag name, you can change
> them to whatever you like.

### Use docker image

This docker image you just built does not contain StarRocks source code repo. You need
to download it first and map it to the container. (You can just use the one you
used to build this image before)

```console
$ docker run -it -v /your/local/path/incubator-doris/:/root/incubator-doris/ doris:v1.0
```

Then you can build source code inside the container.

```console
$ cd /root/incubator-doris/
$ sh build.sh
```

[1]: https://www.oracle.com/technetwork/java/javase/downloads/index.html
