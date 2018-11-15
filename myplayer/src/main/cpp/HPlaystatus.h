//
// Created by yangw on 2018-3-6.
//

#ifndef MYVIDEO_HPLAYSTATUS_H
#define MYVIDEO_HPLAYSTATUS_H


class HPlaystatus {

public:
    bool exit = false;
    bool load = true;
    bool seek = false;
    bool pause = false;

public:
    HPlaystatus();
    ~HPlaystatus();

};


#endif //MYVIDEO_HPLAYSTATUS_H
