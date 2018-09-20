##########################################################################
#
# Builder image: 
# Runs module install.
#
##########################################################################

FROM java:18.3

RUN mkdir -p /scalability
WORKDIR /scalability

# Install dependencies.
COPY package.json yarn.lock /scalability/
RUN yarn install --production && \
  cp -R node_modules node_modules_production && \
  yarn install

COPY . /scalability

RUN javac src/main/java/blockProcessing.java